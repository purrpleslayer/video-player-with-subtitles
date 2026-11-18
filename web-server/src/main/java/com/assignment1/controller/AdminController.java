package com.assignment1.controller;

import java.time.Duration;
import java.time.Instant;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RestController
public class AdminController {

    // Stores the time when the server starts.
    private final Instant serverStart = Instant.now();

     // Tracks whether shutdown has already been requested.
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    private final ConfigurableApplicationContext applicationContext;

    public AdminController(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    // Returns the server start time, current time and total uptime.
    @GetMapping("/api/v1/admin/uptime")
    public UptimeResponse getUptime() {

        Instant now = Instant.now();

        double uptimeSeconds =
                Duration.between(serverStart, now).toMillis() / 1000.0;

        return new UptimeResponse(
                serverStart.toString(),
                now.toString(),
                uptimeSeconds);
    }

    // Requests a graceful shutdown of the application.
    @PostMapping("/api/v1/admin/shutdown")
    public ResponseEntity<?> shutdown() {

        // Only the first shutdown request is accepted.
        if (!shutdownRequested.compareAndSet(false, true)) {

            ErrorResponse error = new ErrorResponse(
                    Instant.now().toString(),
                    409,
                    "Conflict",
                    "Shutdown already in progress.",
                    "/api/v1/admin/shutdown");

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error);
        }

        // Delay so the response return before the server closes.
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                applicationContext.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        return ResponseEntity.accepted()
                .body(new ShutdownResponse(
                        "Graceful shutdown requested."));
    }

    // Response returned by the uptime endpoint.
    public record UptimeResponse(
        String utcServerStart,
        String utcNow,
        double serverUptimeSeconds) {
    }

    // Response returned when shutdown is accepted.
    public record ShutdownResponse(
        String message) {
    }

    // Error response returned when shutdown is already in progress.
    public record ErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String path) {
    }
}