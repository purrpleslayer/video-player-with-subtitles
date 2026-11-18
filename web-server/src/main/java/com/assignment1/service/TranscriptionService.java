package com.assignment1.service;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;


@Service
public class TranscriptionService {

    // Keeps cumulative token counts when requests run concurrently.    
    private final RestClient restClient;
    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();

    // Creates the OpenAI client using the API key supplied at runtime.
    public TranscriptionService(
            @Value("${OPENAI_API_KEY}") String apiKey) {

        restClient = RestClient.builder()
                .baseUrl("https://api.openai.com")
                .defaultHeader(
                        "Authorization", "Bearer " + apiKey)
                .build();
    }

    // Sends the recorded audio to OpenAI and returns the transcription text.
    public String transcribe(MultipartFile file) {
        System.out.println("TranscriptionService received audio file");
        System.out.println("Service file name: " + file.getOriginalFilename());
        System.out.println("Service file size: " + file.getSize());
        System.out.println("Service content type: " + file.getContentType());

        // Build the multipart request required by the transcription API.
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        body.add("model", "gpt-4o-mini-transcribe");

        HttpHeaders fileHeaders = new HttpHeaders();

        // Keep the audio content type sent by the browser.
        if (file.getContentType() != null) {
            fileHeaders.setContentType(
                    MediaType.parseMediaType(file.getContentType()));
        }

        // Include the original file name in the multipart request.
        fileHeaders.setContentDispositionFormData(
                "file", file.getOriginalFilename());

        HttpEntity<Resource> filePart =
                new HttpEntity<>(file.getResource(), fileHeaders);

        body.add("file", filePart);

        // Send the audio to the OpenAI transcription endpoint.
        OpenAIResponse response = restClient.post()
                .uri("/v1/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(OpenAIResponse.class);

        // Add this request's token usage to the running totals.
        inputTokens.addAndGet(response.usage().input_tokens());
        outputTokens.addAndGet(response.usage().output_tokens());

        return response.text();
    }

        // Used by the global statistics endpoint.
        public long getInputTokens() {
                return inputTokens.get();
        }

        public long getOutputTokens() {
                return outputTokens.get();
        }

        // Maps the parts of the OpenAI response that the application needs.
        private record OpenAIResponse(
                String text,
                Usage usage) {
        }

        private record Usage(
                long input_tokens,
                long output_tokens) {
        }   
}

