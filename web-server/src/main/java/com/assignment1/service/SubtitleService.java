package com.assignment1.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.Resource;

import tools.jackson.databind.JsonNode;

@Service
public class SubtitleService {

    private final RestClient restClient;

    // Configure the OpenAI client using the API key supplied at runtime.
    public SubtitleService(@Value("${OPENAI_API_KEY}") String apiKey) {
        restClient = RestClient.builder()
                .baseUrl("https://api.openai.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    // Sends audio to Whisper and returns the transcription with timestamps.
    public JsonNode transcribeSubtitles(MultipartFile file) {

        // Build a multipart request to send the audio and Whisper settings together.
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        // Request detailed JSON with timestamps for words and speech segments.
        body.add("model", "whisper-1");
        body.add("response_format", "verbose_json");
        body.add("timestamp_granularities[]", "word");
        body.add("timestamp_granularities[]", "segment");

        // Preserve the uploaded audio's content type and original filename.
        HttpHeaders fileHeaders = new HttpHeaders();

        if (file.getContentType() != null) {
            fileHeaders.setContentType(
                    MediaType.parseMediaType(file.getContentType()));
        }

        fileHeaders.setContentDispositionFormData(
                "file",
                file.getOriginalFilename() == null
                        ? "audio.wav" : file.getOriginalFilename());

        // Attach the audio and its headers as a separate multipart section.
        HttpEntity<Resource> filePart =
                new HttpEntity<>(file.getResource(), fileHeaders);

        body.add("file", filePart);

        // Send the request to Whisper and return its timestamped JSON response.
        return restClient.post()
                .uri("/v1/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }
}