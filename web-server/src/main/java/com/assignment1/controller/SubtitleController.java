package com.assignment1.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.assignment1.dto.TranscriptionDTO;
import com.assignment1.service.SubtitleService;


@RestController
public class SubtitleController {

    private final SubtitleService subtitleService;

    // Inject the service responsible for subtitle transcription.
    public SubtitleController(SubtitleService subtitleService) {
        this.subtitleService = subtitleService;
    }

    // Receives audio and returns Whisper's timestamped transcription.
    @PostMapping("/api/v1/subtitles")
    public TranscriptionDTO subtitles(@RequestParam("file") MultipartFile file) {
        return subtitleService.transcribeSubtitles(file);
    }
}
