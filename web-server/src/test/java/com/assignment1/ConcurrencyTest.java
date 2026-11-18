package com.assignment1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

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
    properties = "OPENAI_API_KEY=test"
)

class ConcurrencyTest {

    @LocalServerPort
    private int port;

    // Mocked so the test does not send real requests to OpenAI.
    @MockitoBean
    private TranscriptionService transcriptionService;

    // Tracks how many requests are being processed at the same time.
    private final AtomicInteger activeRequests = new AtomicInteger();
    private final AtomicInteger maxActiveRequests = new AtomicInteger();

    // Checks that more than 200 blocking requests can be handled concurrently.
    @Test
    void handlesMoreThan200Requests() throws Exception {

        // Simulates blocking cloud request.
        when(transcriptionService.transcribe(any(MultipartFile.class)))
                .thenAnswer(request -> {

                    int active = activeRequests.incrementAndGet();

                    // Keep track of the highest number of active requests.
                    maxActiveRequests.updateAndGet(
                            current -> Math.max(current, active));

                    Thread.sleep(1000);

                    activeRequests.decrementAndGet();

                    return "test";
                });

        int requestCount = 210;

        // Run each request using a virtual thread.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            List<Future<Integer>> results = new ArrayList<>();

            // Send all requests to the transcription endpoint.
            for (int i = 0; i < requestCount; i++) {
                results.add(executor.submit(this::sendRequest));
            }

            // Check that every request completed successfully.
            for (Future<Integer> result : results) {
                assertEquals(200, result.get());
            }
        }

        assertTrue(maxActiveRequests.get() > 200);
    }

    // Sends one fake audio request to the real controller endpoint.
    private int sendRequest() {

        MultiValueMap<String, Object> body =
                new LinkedMultiValueMap<>();

        // Fake audio is enough cuz the transcription service is mocked.
        body.add("file", new ByteArrayResource("fake audio".getBytes()) {

            @Override
            public String getFilename() {
                return "test.webm";
            }
        });

        return RestClient.create()
                .post()
                .uri("http://localhost:" + port + "/api/transcribe")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .value();
    }
}