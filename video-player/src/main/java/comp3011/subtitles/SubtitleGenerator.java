package comp3011.subtitles;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.concurrent.Callable;

import org.bytedeco.javacv.FFmpegFrameGrabber;

public class SubtitleGenerator implements AutoCloseable {

    private static final long CHUNK_DURATION_US = 15_000_000;

    // Keep subtitle generation on its own thread so playback doesn't freeze.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final AudioExtractor audioExtractor = new AudioExtractor();
    private final SubtitleClient subtitleClient = new SubtitleClient();

    // Extract audio chunks and get subtitles from the server in the background.
    public CompletableFuture<Void> generate(
            File videoFile,
            Consumer<List<SubtitleClient.SubtitleSegment>> onSubtitles) {

        return CompletableFuture.runAsync(() -> {
            try {
                long videoDurationUs;

                // Get the video duration to know when to stop.
                try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
                    grabber.start();
                    videoDurationUs = grabber.getLengthInTime();
                }

                // Process the video in 15-second chunks.
                for (long startUs = 0; startUs < videoDurationUs; startUs += CHUNK_DURATION_US) {
                    File audioFile = null;

                    try {
                        long chunkDurationUs = Math.min(CHUNK_DURATION_US, videoDurationUs - startUs);
                        audioFile = audioExtractor.extractChunk(videoFile, startUs, chunkDurationUs);

                        // Retry this audio chunk if the server is temporarily unavailable.
                        final File chunkFile = audioFile;
                        var response = transcribeWithRetry(
                            () -> subtitleClient.transcribe(chunkFile)
                        );
                        
                        // Adjust the timestamps to match the original video.
                        long chunkStartUs = startUs;

                        List<SubtitleClient.SubtitleSegment> segments = response.segments()
                                .stream()
                                .map(segment -> new SubtitleClient.SubtitleSegment(
                                        segment.startUs() + chunkStartUs,
                                        segment.endUs() + chunkStartUs,
                                        segment.text()))
                                .toList();

                        // Send each chunk's subtitles back as soon as they're ready.
                        onSubtitles.accept(segments);

                    } finally {
                        // Delete the temporary WAV file once processing finishes.
                        if (audioFile != null) {
                            audioFile.delete();
                        }
                    }
                }

            } catch (Exception e) {
                // Pass any errors back to the caller.
                throw new CompletionException(e);
            }
        }, executor);
    }

    // Shut down the background thread when it's no longer needed.
    @Override
    public void close() {
        executor.shutdownNow();
    }

    // Retry failed transcription requests without losing successful chunks.
    private <T> T transcribeWithRetry(Callable<T> request) throws Exception {
        final int maxAttempts = 30;
        final long retryDelayMs = 4_000;

        for (int attempt = 1; ; attempt++) {
            try {
                return request.call();

            } catch (java.io.IOException e) {
                String message = e.getMessage();

                // Don't repeatedly retry requests that are clearly invalid.
                boolean permanentError = message != null
                        && (message.contains("HTTP 400")
                        || message.contains("HTTP 401")
                        || message.contains("HTTP 403")
                        || message.contains("HTTP 413"));

                if (permanentError || attempt >= maxAttempts) {
                    throw e;
                }

                System.err.printf(
                        "Transcription failed: %s. Retrying chunk in 4 seconds (%d/%d)%n",
                        message, attempt, maxAttempts
                );

                Thread.sleep(retryDelayMs);
            }
        }
    }
}
