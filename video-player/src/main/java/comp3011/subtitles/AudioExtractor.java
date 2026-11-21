package comp3011.subtitles;

import java.io.File;
import java.io.IOException;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S16LE;

// Extracts audio into temporary WAV files for subtitle transcription.
// This runs on whichever thread calls it.
public class AudioExtractor {

    // Use a separate grabber so extracting audio doesn't interfere with video playback.
    public File extract(File videoFile) throws Exception {
        return extractChunk(videoFile, 0, Long.MAX_VALUE);
    }

    // Extract a section of audio using microsecond timestamps.
    public File extractChunk(File videoFile, long startUs, long durationUs) throws Exception {

        if (startUs < 0 || durationUs <= 0 || durationUs > Long.MAX_VALUE - startUs) {
            throw new IllegalArgumentException("Invalid audio chunk time range.");
        }

        long endUs = startUs + durationUs;

        // Create a temporary WAV file to store the extracted audio.
        File audioFile = File.createTempFile("subtitles-", ".wav");

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
            grabber.start();

            // No audio no transcription
            if (!grabber.hasAudio()) {
                throw new IOException("The video does not contain audio.");
            }

            // Move to the start of the requested audio chunk.
            if (startUs > 0) {
                grabber.setAudioTimestamp(startUs);
            }
            
            // Convert the audio into a WAV file that Whisper can process
            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(audioFile, 1)) {

                // Use mono, 16 kHz audio to keep the file smaller.
                recorder.setFormat("wav");
                recorder.setSampleRate(16000);
                recorder.setAudioChannels(1);
                recorder.setAudioCodec(AV_CODEC_ID_PCM_S16LE);
                recorder.start();

                // Read only the audio samples and write them into the WAV file.
                Frame frame;
                while ((frame = grabber.grabSamples()) != null) {

                    if (frame.timestamp >= endUs) {
                        break;
                    }

                    if (frame.samples != null) {
                        recorder.recordSamples(
                                grabber.getSampleRate(),
                                grabber.getAudioChannels(),
                                frame.samples);
                    }
                }
            }

        } catch (Exception e) {

            // Do not leave an incomplete audio file if extraction fails.
            audioFile.delete();
            throw e;
        }

        return audioFile;
    }
}