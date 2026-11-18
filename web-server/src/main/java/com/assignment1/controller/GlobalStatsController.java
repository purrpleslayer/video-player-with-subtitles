package com.assignment1.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.assignment1.service.TranscriptionService;

@RestController
public class GlobalStatsController {

    // Service used to read the current token totals.
    private final TranscriptionService transcriptionService;

    public GlobalStatsController(TranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    // Returns the total input and output tokens used since server start.
    @GetMapping("/api/v1/global/stats")
    public StatsResponse getStats() {

        return new StatsResponse(
                transcriptionService.getInputTokens(),
                transcriptionService.getOutputTokens());
    }

    // Response returned by the global statistics endpoint.
    public record StatsResponse(
            long inputTokens,
            long outputTokens) {
    }
}