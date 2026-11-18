/*
 * Starter code supplied for Adelaide University COMP3011 Assignment 2.
 * Students are free to modify this file for assessment purposes.
 * 
 * Authors:
 *   1. Simon Ratcliffe, in collaboration with GPT-5.6 Terra
 *   2. Suhanna Singh - a2960347
 *
 * Copyright 2026 Simon Ratcliffe
 */
package comp3011;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/**
 * The model part of the video player model-view-controller architecture.
 *
 * <p>
 * Owns playback timing, seeking and audio scheduling. Video decoding runs on a
 * background thread and effects are processed by a pool of worker threads.
 * The completed frames are displayed on the JavaFX thread through callbacks
 * supplied by its owning {@link VideoPlayerController}.
 * </p>
 */
public class VideoPlayerModel {
    private static final long NO_SEEK_REQUEST = -1; // Sentinel value used when no seek position is active.
    private static final long FIVE_SECONDS_US = 5_000_000L;
    private static final long AUDIO_LEAD_NS = 30_000_000L;

    private final List<Character> requestedEffects;
    private final Queue<PendingAudio> pendingAudio = new ArrayDeque<>();
    
    // Limits how many processed frames and audio events can wait for JavaFX,
    // preventing the decoder from getting too far ahead of playback.
    private final BlockingQueue<OutputEvent> completed = new ArrayBlockingQueue<>(24);
    private final BiConsumer<Integer, Integer> videoSizeChangedHandler;
    private final Consumer<Image> frameReadyHandler;
    private final Consumer<String> statusChangedHandler;
    private final BiConsumer<Boolean, Boolean> playbackStateChangedHandler;
    private final Consumer<Boolean> audioOutputStateChangedHandler;

	// Keep FFmpeg decoding off the JavaFX thread so playback controls
	// and rendering are not blocked by decoding.
    private final ExecutorService decoderExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "video-decoder");
        thread.setDaemon(true);
        return thread;
    });

	// Each playback session has a generation number so results from an old
	// session are ignored after seeking, restarting or stopping playback.
    private volatile long playbackGeneration;
    private volatile boolean shuttingDown;
    private File videoFile; // Path to the media that comes from the command line
    private AudioPlayer audioPlayer; // This is ours. It has some real time buffering smarts.
    private PreparedFrame preparedFrame;
    private boolean playbackOpen;
    private boolean pauseRequested;
    private boolean audioOutputEnabled;
    private boolean endReceived;
    private boolean displayedSinceSeek;
    private long currentTimestampUs;
    private long videoDurationUs = NO_SEEK_REQUEST;
    private int videoFrameDurationUs;
    private int intFrameRate;
    private long firstTimestampUs = NO_SEEK_REQUEST;
    private long logicalPlaybackBaseUs;
    private long playbackStartNs;
    private long pauseStartedNs;
    private long relativeSeekBaseUs = NO_SEEK_REQUEST;

    // This is the critical wiring that allows the framework (JavaFX) to call
    // into our model logic every time it goes around its event loop. Every GUI
    // system has an event loop so that button clicks, key presses and window
    // resizes can be responded to. Most offer ways of running additional logic
    // in either idle time (when there are no pending UI events to process) or
    // on a regular heart beat, such as with this JavaFX AnimationTimer. Here
    // we just create a little anonymous local subclass and override the handle
    // method to call the method we want run every heart beat.
    private final AnimationTimer playbackTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            pumpPlayback(now);
        }
    };

    private record PixelFrame(byte[] pixels, int width, int height, int stride,
                              int channels, int frameNumber, long timestampUs,
                              long preparedAtNs) {
    }

    private record OutputEvent(long generation, PixelFrame frame,
                               PendingAudio audio, Exception error, boolean end) {
    }

    private record PreparedFrame(Image image, int frameNumber, long logicalTimestampUs,
                                 long targetTimeNs, long preparedAtNs) {
    }

    public VideoPlayerModel(
            boolean audioEnabled,
            List<Character> requestedEffects,
            BiConsumer<Integer, Integer> videoSizeChangedHandler,
            Consumer<Image> frameReadyHandler,
            Consumer<String> statusChangedHandler,
            BiConsumer<Boolean, Boolean> playbackStateChangedHandler,
            Consumer<Boolean> audioOutputStateChangedHandler) {
        this.audioOutputEnabled = audioEnabled;
        this.requestedEffects = List.copyOf(requestedEffects);
        this.videoSizeChangedHandler = videoSizeChangedHandler;
        this.frameReadyHandler = frameReadyHandler;
        this.statusChangedHandler = statusChangedHandler;
        this.playbackStateChangedHandler = playbackStateChangedHandler;
        this.audioOutputStateChangedHandler = audioOutputStateChangedHandler;
    }

    public void play(File file) {
        videoFile = file;
        videoDurationUs = NO_SEEK_REQUEST;
        videoFrameDurationUs = 0;
        startPlayback(0, false, NO_SEEK_REQUEST);
    }

    public void startOver() {
        if (videoFile == null) {
            return;
        }

        seekTo(0);
    }

    public void backFiveSeconds() {
        seekRelative(-FIVE_SECONDS_US);
    }

    public void forwardFiveSeconds() {
        seekRelative(FIVE_SECONDS_US);
    }

    public void togglePause() {
        if (!playbackOpen) {
            if (videoFile != null) {
                startPlayback(displayableSeekTimestamp(currentTimestampUs), false, currentTimestampUs);
            }
            return;
        }

        pauseRequested = !pauseRequested;
        relativeSeekBaseUs = NO_SEEK_REQUEST;
        if (pauseRequested) {
            pauseStartedNs = System.nanoTime();
            flushAudioOutput();
        } else {
            resumePlaybackClock(System.nanoTime());
        }
        notifyPlaybackStateChanged();
    }

    public void toggleAudioOutput() {
        audioOutputEnabled = !audioOutputEnabled;
        pendingAudio.clear();
        flushAudioOutput();
        notifyAudioOutputStateChanged();
    }

    public boolean isAudioOutputEnabled() {
        return audioOutputEnabled;
    }

    public void stopPlayback() {
        closePlaybackResources();
        currentTimestampUs = 0;
        pauseRequested = false;
        notifyStatusChanged("Stopped");
        notifyFrameReady(null);
        notifyPlaybackStateChanged();
    }

    public void shutdown() {
        closePlaybackResources();
        shuttingDown = true;
        decoderExecutor.shutdown();
    }

    private void seekRelative(long offsetUs) {
        if (videoFile == null) {
            return;
        }

        long baseTimestampUs = relativeSeekBaseUs != NO_SEEK_REQUEST
                ? relativeSeekBaseUs
                : currentTimestampUs;
        seekTo(baseTimestampUs + offsetUs);
    }

    private void seekTo(long timestampUs) {
        long logicalUs = clampSeekTimestamp(timestampUs);
        startPlayback(displayableSeekTimestamp(logicalUs), pauseRequested, logicalUs);
    }

    private void startPlayback(long startUs, boolean initiallyPaused, long logicalUs) {
        closePlaybackResources();
        if (shuttingDown || videoFile == null) {
            return;
        }

        pauseRequested = initiallyPaused;
        long baseUs = logicalUs == NO_SEEK_REQUEST ? startUs : logicalUs;
        resetPlaybackClock(baseUs);
        notifyStatusChanged(videoFile.getName());
        playbackOpen = true;
        playbackTimer.start();
        notifyPlaybackStateChanged();

        long generation = playbackGeneration;
        File file = videoFile;
        decoderExecutor.execute(() -> decodeSession(file, startUs, generation));
    }

	// Claude was consulted for robustness improvements involving concurrent
	// frame processing, resource handling and playback stability.
    private void decodeSession(File file, long startUs, long generation) {
        int workers = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 2));
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file);
             VideoProcessor<PixelFrame> processor = new VideoProcessor<>(workers, requestedEffects)) {
            grabber.setImageMode(FrameGrabber.ImageMode.COLOR);
            grabber.setSampleMode(FrameGrabber.SampleMode.SHORT);
            grabber.start();
            boolean hasAudio = grabber.hasAudio();
            double frameRate = grabber.getFrameRate();
            int roundedRate = (int) Math.round(frameRate);
            int frameDuration = frameRate > 0 ? (int) Math.round(1_000_000.0 / frameRate) : 0;
            int frameCount = grabber.getLengthInVideoFrames();
            long duration = grabber.getLengthInTime();
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            int sampleRate = grabber.getSampleRate();
            int channels = grabber.getAudioChannels();
            int pixelFormat = grabber.getPixelFormat();

            Platform.runLater(() -> {
                if (!isCurrent(generation)) {
                    return;
                }
                intFrameRate = roundedRate;
                videoFrameDurationUs = frameDuration;
                videoDurationUs = duration > 0 ? duration : NO_SEEK_REQUEST;
                notifyVideoSizeChanged(width, height);
                if (audioPlayer != null) {
                    audioPlayer.close();
                }
                audioPlayer = new AudioPlayer();
                if (hasAudio) {
                    try {
                        audioPlayer.open(sampleRate, channels);
                    } catch (Exception e) {
                        handlePlaybackError(e);
                    }
                }
            });

            if (startUs > 0) {
                grabber.setTimestamp(startUs);
            }

            InfoVideo infoVideo = null;
            // This instance is used only to convert sample buffers, not to open a sound device.
            AudioPlayer sampleCopier = new AudioPlayer();
            boolean eof = false;

            while (isCurrent(generation)) {
                // Release completed results in submission order, not worker completion order.
                PixelFrame ready = processor.pollReady();
                if (ready != null) {
                    if (!sendEvent(new OutputEvent(generation, ready, null, null, false), generation)) break;
                }

                if (eof) {
                    if (!processor.hasPending()) {
                        sendEvent(new OutputEvent(generation, null, null, null, true), generation);
                        break;
                    }
                } else if (processor.canSubmit()) {
                    Frame frame = hasAudio ? grabber.grab() : grabber.grabImage();
                    if (frame == null) {
                        eof = true;
                        continue;
                    }
                    long timestampUs = grabber.getTimestamp();
                    if (hasAudio && frame.samples != null) {
                        byte[] samples = sampleCopier.copySamples(frame);
                        if (samples.length > 0 && !sendEvent(new OutputEvent(generation, null,
                                new PendingAudio(timestampUs, samples), null, false), generation)) break;
                    }
                    if (frame.image == null) {
                        continue;
                    }
                    if (infoVideo == null) {
                        infoVideo = new InfoVideo(mediaName(file), frameCount, frame.imageWidth,
                                frame.imageHeight, frame.imageDepth, frame.imageChannels,
                                frame.imageStride, frameRate, roundedRate, frameDuration, pixelFormat);
                    }
                    int frameNumber = grabber.getFrameNumber();
                    processor.submitFrame(frame, new InfoFrame(frameNumber, timestampUs), infoVideo,
                            processed -> copyProcessedFrame(processed, frameNumber, timestampUs));
                }
                if (ready == null && (eof || !processor.canSubmit())) {
                    Thread.sleep(1);
                }
            }
        } catch (Exception e) {
            if (isCurrent(generation)) {
                sendEvent(new OutputEvent(generation, null, null, e, false), generation);
            }
        }
    }

    // Copy the processed pixels into a separate array so the original native
    // frame can be released without affecting the image sent to JavaFX.
    private PixelFrame copyProcessedFrame(Frame frame, int frameNumber, long timestampUs) {
        if (frame.image == null || !(frame.image[0] instanceof ByteBuffer pixels)) {
            throw new IllegalArgumentException("Expected byte-backed video frame");
        }
        int stride = frame.imageStride;
        int height = frame.imageHeight;
        byte[] data = new byte[Math.multiplyExact(stride, height)];
        ByteBuffer source = pixels.duplicate();
        source.position(0);
        source.get(data);
        if (frame.imageChannels == 3) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < frame.imageWidth; x++) {
                    int offset = y * stride + x * 3;
                    byte blue = data[offset];
                    data[offset] = data[offset + 2];
                    data[offset + 2] = blue;
                }
            }
        }
        return new PixelFrame(data, frame.imageWidth, height, stride, frame.imageChannels,
                frameNumber, timestampUs, System.nanoTime());
    }

    private boolean sendEvent(OutputEvent event, long generation) {
        try {
            while (isCurrent(generation)) {
                if (completed.offer(event, 20, TimeUnit.MILLISECONDS)) return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private boolean isCurrent(long generation) {
        return !shuttingDown && playbackGeneration == generation;
    }

    private void resetPlaybackClock(long logicalUs) {
        pendingAudio.clear();
        completed.clear();
        preparedFrame = null;
        endReceived = false;
        displayedSinceSeek = false;
        currentTimestampUs = logicalUs;
        relativeSeekBaseUs = logicalUs;
        firstTimestampUs = NO_SEEK_REQUEST;
        logicalPlaybackBaseUs = logicalUs;
        playbackStartNs = 0;
        pauseStartedNs = pauseRequested ? System.nanoTime() : 0;
    }

    private void resumePlaybackClock(long now) {
        if (pauseStartedNs > 0 && playbackStartNs > 0) {
            playbackStartNs += now - pauseStartedNs;
        }
        pauseStartedNs = 0;
    }

    // Receive processed frames on the JavaFX thread and display them according
    // to their timestamps, while decoding and processing continue separately.
    private void pumpPlayback(long now) {
        if (!playbackOpen) {
            return;
        }

        // Audio events are drained even when paused, but queued samples are not played.
        for (int count = 0; count < 32 && preparedFrame == null; count++) {
            OutputEvent event = completed.poll();
            if (event == null) break;
            if (event.generation() != playbackGeneration) continue;
            if (event.error() != null) {
                handlePlaybackError(event.error());
                return;
            }
            if (event.end()) {
                endReceived = true;
                break;
            }
            if (event.audio() != null) {
                if (audioOutputEnabled) pendingAudio.add(event.audio());
                continue;
            }
            PixelFrame frame = event.frame();
            if (frame != null) {
                if (firstTimestampUs == NO_SEEK_REQUEST) {
                    firstTimestampUs = frame.timestampUs();
                    playbackStartNs = now;
                    if (pauseRequested) pauseStartedNs = now;
                }
                Image image = createImage(frame);
                long relativeUs = Math.max(0, frame.timestampUs() - firstTimestampUs);
                preparedFrame = new PreparedFrame(image, frame.frameNumber(),
                        logicalPlaybackBaseUs + relativeUs,
                        playbackStartNs + relativeUs * 1000L, frame.preparedAtNs());
            }
        }

        writeDueAudio(now);
        if (preparedFrame != null && preparedFrame.targetTimeNs() <= now &&
                (!pauseRequested || !displayedSinceSeek)) {
            displayPreparedFrame();
        }
        if (endReceived && preparedFrame == null && completed.isEmpty() && pendingAudio.isEmpty()) {
            finishPlayback();
        }
    }

    private Image createImage(PixelFrame frame) {
        WritableImage image = new WritableImage(frame.width(), frame.height());
        if (frame.channels() == 3) {
            image.getPixelWriter().setPixels(0, 0, frame.width(), frame.height(),
                    PixelFormat.getByteRgbInstance(), frame.pixels(), 0, frame.stride());
        } else if (frame.channels() == 4) {
            image.getPixelWriter().setPixels(0, 0, frame.width(), frame.height(),
                    PixelFormat.getByteBgraInstance(), frame.pixels(), 0, frame.stride());
        } else {
            throw new IllegalArgumentException("Unsupported image channel count: " + frame.channels());
        }
        return image;
    }

    private void displayPreparedFrame() {
        PreparedFrame frame = preparedFrame;
        preparedFrame = null;
        displayedSinceSeek = true;
        currentTimestampUs = frame.logicalTimestampUs();
        relativeSeekBaseUs = NO_SEEK_REQUEST;
        // Dump some logging to the console once per second so that real time performance can be monitored.
        if (intFrameRate > 0 && frame.frameNumber() % intFrameRate == 0) {
            long remainingUs = (frame.targetTimeNs() - frame.preparedAtNs()) / 1_000L;
            if (remainingUs < 0) {
                System.out.printf("Frame headroom is \u001B[31m%5dus\u001B[0m out of %dus spare.%n",
                        remainingUs,
                        videoFrameDurationUs);
            } else {
                System.out.printf("Frame headroom is \u001B[32m%5dus\u001B[0m out of %dus spare.%n",
                        remainingUs,
                        videoFrameDurationUs);
            }
        }
        notifyFrameReady(frame.image());
    }

    private void writeDueAudio(long now) {
        // Handle some obvious early exits
        if (!audioOutputEnabled || audioPlayer == null) {
            pendingAudio.clear();
            return;
        }
        if (pauseRequested || firstTimestampUs == NO_SEEK_REQUEST || playbackStartNs <= 0) {
            return;
        }

        // Here is the real time dependent logic
        long dueTimestampUs = firstTimestampUs + (now + AUDIO_LEAD_NS - playbackStartNs) / 1_000L;
        while (!pendingAudio.isEmpty()) {
            PendingAudio audio = pendingAudio.peek();
            if (audio.timestampUs() > dueTimestampUs) {
                return;
            }

            int written = audioPlayer.write(audio.samples(), audio.offset(), audio.remaining());
            if (written <= 0) {
                return;
            }

            audio.advance(written);
            if (!audio.finished()) {
                return;
            }
            pendingAudio.remove();
        }
    }

    private void finishPlayback() {
        long endTimestampUs = videoDurationUs != NO_SEEK_REQUEST
                ? videoDurationUs
                : currentTimestampUs;

        closePlaybackResources();
        pauseRequested = true;
        currentTimestampUs = endTimestampUs;
        relativeSeekBaseUs = endTimestampUs;
        notifyStatusChanged("Playback finished");
        notifyPlaybackStateChanged();
    }

    private void handlePlaybackError(Exception e) {
        e.printStackTrace();
        closePlaybackResources();
        pauseRequested = false;
        notifyStatusChanged("Error: " + e.getMessage());
        notifyPlaybackStateChanged();
    }

    private void closePlaybackResources() {
        playbackGeneration++;
        playbackTimer.stop();
        playbackOpen = false;
        preparedFrame = null;
        completed.clear();
        pendingAudio.clear();
        firstTimestampUs = NO_SEEK_REQUEST;
        playbackStartNs = 0;
        pauseStartedNs = 0;
        endReceived = false;
        if (audioPlayer != null) {
            audioPlayer.close();
            audioPlayer = null;
        }
        // The decoder owns its grabber and processor and closes both on its own thread.
    }

    private String mediaName(File file) {
        String fileName = file.getName();
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart <= 0) {
            return fileName;
        }
        return fileName.substring(0, extensionStart);
    }

    private long clampSeekTimestamp(long timestampUs) {
        long clampedTimestampUs = Math.max(0, timestampUs);
        long durationUs = videoDurationUs;
        if (durationUs != NO_SEEK_REQUEST) {
            clampedTimestampUs = Math.min(clampedTimestampUs, durationUs);
        }
        return clampedTimestampUs;
    }

    private long displayableSeekTimestamp(long logicalTimestampUs) {
        long durationUs = videoDurationUs;
        if (durationUs == NO_SEEK_REQUEST || logicalTimestampUs < durationUs) {
            return logicalTimestampUs;
        }

        int frameDurationUs = videoFrameDurationUs;
        if (frameDurationUs <= 0) {
            return Math.max(0, durationUs - 1);
        }

        return Math.max(0, durationUs - frameDurationUs);
    }

    private void flushAudioOutput() {
        if (audioPlayer != null) {
            audioPlayer.flush();
        }
    }

    private void notifyVideoSizeChanged(int width, int height) {
        videoSizeChangedHandler.accept(width, height);
    }

    private void notifyFrameReady(Image image) {
        frameReadyHandler.accept(image);
    }

    private void notifyStatusChanged(String status) {
        statusChangedHandler.accept(status);
    }

    private void notifyPlaybackStateChanged() {
        playbackStateChangedHandler.accept(playbackOpen, pauseRequested);
    }

    private void notifyAudioOutputStateChanged() {
        audioOutputStateChangedHandler.accept(audioOutputEnabled);
    }

    private static class PendingAudio {
        private final long timestampUs;
        private final byte[] samples;
        private int offset;

        PendingAudio(long timestampUs, byte[] samples) {
            this.timestampUs = timestampUs;
            this.samples = samples;
        }

        long timestampUs() {
            return timestampUs;
        }

        byte[] samples() {
            return samples;
        }

        int offset() {
            return offset;
        }

        int remaining() {
            return samples.length - offset;
        }

        void advance(int byteCount) {
            offset += byteCount;
        }

        boolean finished() {
            return offset >= samples.length;
        }
    }
}
