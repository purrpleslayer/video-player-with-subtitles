package com.assignment1.controller;

import com.assignment1.service.SubtitleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class UsageController {

    private final SubtitleService subtitleService;

    public UsageController(SubtitleService subtitleService) {
        this.subtitleService = subtitleService;
    }

    @GetMapping("/api/v1/global/usage")
    public Map<String, Double> getUsage() {
        return Map.of("usage", subtitleService.getTotalUsageSeconds());
    }
}