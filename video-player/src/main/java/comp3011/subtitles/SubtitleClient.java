package comp3011.subtitles;

import java.util.List;

public class SubtitleClient {

    // Stores the subtitles and usage returned by the server.
    public record SubtitleResponse(
            List<SubtitleSegment> segments,
            Usage usage) {
    }

    // Stores each subtitle's text and timing.
    public record SubtitleSegment(
            long startUs,
            long endUs,
            String text) {
    }

    // Tracks how many seconds of audio Whisper processed.
    public record Usage(double seconds) {
    }
}
