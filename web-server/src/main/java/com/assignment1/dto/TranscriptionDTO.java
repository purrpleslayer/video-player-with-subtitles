package com.assignment1.dto;

import java.util.List;

// Defines the response sent to the video player, containing subtitle segments
// and the amount of audio processed by Whisper.
public record TranscriptionDTO(
        List<SubtitleSegment> segments,
        Usage usage) {

    // Keeps track of audio duration in seconds for API usage reporting.
    public record Usage(double seconds) {
    }

    // Stores when each subtitle should appear and disappear during playback.
    // Microseconds are used to match timing format of the FFmpeg video player.
    public record SubtitleSegment(
            long startUs,
            long endUs,
            String text) {
    }
}