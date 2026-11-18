package com.assignment1.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.assignment1.service.TranscriptionService;

@RestController
public class TranscriptionController {

    private final TranscriptionService transcriptionService;

    public TranscriptionController(TranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    @PostMapping("/api/transcribe")
    public String transcribe(@RequestParam("file") MultipartFile file) {

        System.out.println("Received audio file");
        System.out.println("File name: " + file.getOriginalFilename());
        System.out.println("File size: " + file.getSize());

        return transcriptionService.transcribe(file);
    }
}

