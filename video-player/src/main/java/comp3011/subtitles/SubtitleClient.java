package comp3011.subtitles;

import java.util.List;
import java.net.http.HttpClient;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

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

    // Prepare the WAV file as a multipart request for the Spring Boot server.
    private HttpRequest buildRequest(File audioFile) throws IOException {
        String multipartBoundary = UUID.randomUUID().toString();

        // Server expects audio under the field name "file".
        String audioPartHeader = "--" + multipartBoundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n";

        String multipartClosingBoundary = "\r\n--" + multipartBoundary + "--\r\n";

        // Put the audio file between the header and closing boundary.
        return HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "multipart/form-data; boundary=" + multipartBoundary)
                .POST(HttpRequest.BodyPublishers.concat(
                        HttpRequest.BodyPublishers.ofString(audioPartHeader, StandardCharsets.UTF_8),
                        HttpRequest.BodyPublishers.ofFile(audioFile.toPath()),
                        HttpRequest.BodyPublishers.ofString(multipartClosingBoundary, StandardCharsets.UTF_8)))
                .build();
    }

    // Send the audio to the server and get back the generated subtitles.
    public SubtitleResponse transcribe(File audioFile)
            throws IOException, InterruptedException {

        HttpRequest request = buildRequest(audioFile);

        // Send the request and wait for the server to finish transcribing.
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Report the error if server fails
        if (response.statusCode() != 200) {
            throw new IOException(
                    "Subtitle request failed (HTTP " + response.statusCode() + ")");
        }

        // Turn the JSON response into Java records.
        return mapper.readValue(response.body(), SubtitleResponse.class);
    }

}