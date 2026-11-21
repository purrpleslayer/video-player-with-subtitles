package comp3011.subtitles;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bytedeco.javacpp.Loader;

// Extracts audio into temporary WAV files for subtitle transcription.
// This runs on whichever thread calls it.
public class AudioExtractor {

    // Use a separate process so extracting audio doesn't interfere with video playback.
    public File extract(File videoFile) throws Exception {
        return extractChunk(videoFile, 0, Long.MAX_VALUE);
    }

    // Extract a section of audio using microsecond timestamps.
    public File extractChunk(File videoFile, long startUs, long durationUs) throws Exception {

        if (startUs < 0 || durationUs <= 0 || durationUs > Long.MAX_VALUE - startUs) {
            throw new IllegalArgumentException("Invalid audio chunk time range.");
        }

        // Create a temporary WAV file to store the extracted audio.
        File audioFile = File.createTempFile("subtitles-", ".wav");

        // Let FFmpeg handle the audio conversion instead of recording frames manually.
        List<String> command = new ArrayList<>(List.of(
                Loader.load(org.bytedeco.ffmpeg.ffmpeg.class), "-hide_banner", "-loglevel", "error", "-y",
                "-i", videoFile.getAbsolutePath(),
                "-ss", String.format(Locale.ROOT, "%.6f", startUs / 1_000_000.0)
        ));

        if (durationUs != Long.MAX_VALUE) {
            command.add("-t");
            command.add(String.format(Locale.ROOT, "%.6f", durationUs / 1_000_000.0));
        }

        command.addAll(List.of(
                "-vn", "-ac", "1", "-ar", "16000",
                "-c:a", "pcm_s16le", audioFile.getAbsolutePath()
        ));

        try {
            Process process = new ProcessBuilder(command).inheritIO().start();

            if (process.waitFor() != 0 || audioFile.length() <= 44) {
                throw new IOException("Audio extraction failed.");
            }

            return audioFile;
        } catch (Exception e) {
            // Do not leave an incomplete audio file if extraction fails.
            audioFile.delete();
            throw e;
        }
    }
}

    // Requires the ffmpeg executable on PATH.
    // The previous JavaCV extraction gave inaccurate subtitles in testing;
    // sample conversion or frame boundaries may be involved but no clue :<