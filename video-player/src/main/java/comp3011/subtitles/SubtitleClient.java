package comp3011.subtitles;

import java.util.List;
import java.net.http.HttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SubtitleClient {

    // Spring Boot endpoint for generating subtitles.
    private static final String ENDPOINT = "http://localhost:8080/api/v1/subtitles";

    // Used to communicate with the server and read its JSON response.
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

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