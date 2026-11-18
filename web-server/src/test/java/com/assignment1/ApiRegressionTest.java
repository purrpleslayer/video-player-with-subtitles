package com.assignment1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import com.assignment1.service.TranscriptionService;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "OPENAI_API_KEY=test")
class ApiRegressionTest {

    @LocalServerPort
    private int port;

    // Use a fake service so regression tests do not call OpenAI.
    @MockitoBean
    private TranscriptionService transcriptionService;

    // Checks that the transcription endpoint returns the text from the service.
    @Test
    void transcribeReturnsText() {

        // Make the mocked service return a known transcription.
        when(transcriptionService.transcribe(any(MultipartFile.class)))
                .thenReturn("test transcription");

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        // Fake audio is cuz transcription service is mocked.
        body.add("file",
                new ByteArrayResource("fake audio".getBytes()) {
                    @Override
                    public String getFilename() {
                        return "test.webm";
                    }
                });

        // Send a real HTTP request to the transcription controller.
        String response = RestClient.create()
                .post()
                .uri("http://localhost:" + port + "/api/transcribe")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(String.class);

        // The controller should return the mocked transcription.
        assertEquals("test transcription", response);
    }

    // Checks that the stats endpoint returns the token counts from the service.
    @Test
    void globalStatsReturnsTokenCounts() {

        // Set known token values in the mocked service.
        when(transcriptionService.getInputTokens()).thenReturn(100L);
        when(transcriptionService.getOutputTokens()).thenReturn(25L);

        Map<?, ?> response = RestClient.create()
                .get()
                .uri("http://localhost:" + port + "/api/v1/global/stats")
                .retrieve()
                .body(Map.class);

        // Check that both token totals are returned correctly.
        assertEquals(100, ((Number) response.get("inputTokens")).intValue());
        assertEquals(25, ((Number) response.get("outputTokens")).intValue());
    }

    // Checks that the uptime endpoint returns the required values.
    @Test
    void uptimeReturnsValues() {

        Map<?, ?> response = RestClient.create()
                .get()
                .uri("http://localhost:"
                        + port
                        + "/api/v1/admin/uptime")
                .retrieve()
                .body(Map.class);

        // Required time fields should be present.
        assertTrue(response.containsKey("utcServerStart"));
        assertTrue(response.containsKey("utcNow"));

        double uptime = ((Number) response.get("serverUptimeSeconds")).doubleValue();

        // Uptime should never be negative.
        assertTrue(uptime >= 0);
    }
}