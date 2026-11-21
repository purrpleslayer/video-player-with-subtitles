/*
 * Starter code supplied for Adelaide University COMP3011 Assignment 2.
 * Students are free to modify this file for assessment purposes.
 * 
 * Authors:
 *   1. Simon Ratcliffe, in collaboration with GPT-5.6 Terra
 *   2. <student name and student number insert here upon modification>
 *
 * Copyright 2026 Simon Ratcliffe
 */
package comp3011;

import java.io.File;
import java.util.function.BiConsumer;

import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import java.util.List;
import java.util.Comparator;
import java.util.concurrent.CopyOnWriteArrayList;

import comp3011.subtitles.SubtitleGenerator;

/**
 * The controller part of the video player model-view-controller architecture.
 * 
 * <p>
 * Wires up events from the {@link VideoPlayerView} to the {@link VideoPlayerModel}.
 * This includes button actions, keyboard shortcuts and display resizing.
 * </p>
 */
public class VideoPlayerController {
    private final VideoPlayerModel model;
    private final VideoPlayerView view;
    private final BiConsumer<Integer, Integer> videoSizeChangedHandler;
    private final SubtitleGenerator subtitleGenerator = new SubtitleGenerator();
    private boolean subtitlesEnabled;
    private record SubtitleCue(long startUs, long endUs, String text) {}
    private final List<SubtitleCue> subtitles = new CopyOnWriteArrayList<>();
    

    public VideoPlayerController(
            boolean audioEnabled,
            boolean subtitlesEnabled,
            List<Character> requestedEffects,
            BiConsumer<Integer, Integer> videoSizeChangedHandler) {
        this.view = new VideoPlayerView();
        this.videoSizeChangedHandler = videoSizeChangedHandler;
        this.subtitlesEnabled = subtitlesEnabled;
        model = new VideoPlayerModel(
                audioEnabled,
                requestedEffects,
                this::onVideoSizeChanged,
                this::onFrameReady,
                this::onStatusChanged,
                this::onPlaybackStateChanged,
                this::onAudioOutputStateChanged);

        view.getStartOverButton().setOnAction(_ -> startOver());
        view.getBackFiveButton().setOnAction(_ -> backFiveSeconds());
        view.getPausePlayButton().setOnAction(_ -> togglePause());
        view.getForwardFiveButton().setOnAction(_ -> forwardFiveSeconds());
        view.getStopButton().setOnAction(_ -> stopAndExit());
        view.getAudioButton().setOnAction(_ -> toggleAudioOutput());
        view.showAudioEnabled(model.isAudioOutputEnabled());
    }

    public void installKeyboardShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stopAndExit();
                event.consume();
            } else if (event.getCode() == KeyCode.SPACE) {
                togglePause();
                event.consume();
            } else if (event.getCode() == KeyCode.LEFT) {
                backFiveSeconds();
                event.consume();
            } else if (event.getCode() == KeyCode.RIGHT) {
                forwardFiveSeconds();
                event.consume();
            } else if (event.getCode() == KeyCode.HOME) {
                startOver();
                event.consume();
            } else if (event.getCode() == KeyCode.A) {
                toggleAudioOutput();
                event.consume();
            }
        });
    }

    public VideoPlayerView getView() {
        return view;
    }

    public void play(File file) {
        subtitles.clear();

        view.setPlaybackControlsDisabled(false);
        model.play(file);

        if (subtitlesEnabled) {
            // Start generating subtitles in the background while the video plays.
            subtitleGenerator.generate(file, segments -> {
                // Print each subtitle with its start and end time.
                for (var subtitle : segments) {
                    if (subtitle.text().isBlank()
                            || subtitle.endUs() <= subtitle.startUs()) {
                        continue;
                    }

                    subtitles.add(new SubtitleCue(
                            subtitle.startUs(),
                            subtitle.endUs(),
                            subtitle.text()
                    ));

                    System.out.printf("[%.2fs - %.2fs] %s%n",
                            subtitle.startUs() / 1_000_000.0,
                            subtitle.endUs() / 1_000_000.0,
                            subtitle.text());
                }   

                subtitles.sort(Comparator.comparingLong(SubtitleCue::startUs));
                System.out.println("Stored subtitles: " + subtitles.size());

            }).exceptionally(error -> {
                // Print an error if subtitle generation fails.
                System.err.println("Subtitle generation failed: " + error.getMessage());
                return null;
            });
        }
    }

    public void shutdown() {
        subtitleGenerator.close();
        model.shutdown();
    }

    private void startOver() {
        model.startOver();
    }

    private void backFiveSeconds() {
        model.backFiveSeconds();
    }

    private void togglePause() {
        model.togglePause();
    }

    private void forwardFiveSeconds() {
        model.forwardFiveSeconds();
    }

    private void toggleAudioOutput() {
        model.toggleAudioOutput();
    }

    private void stopAndExit() {
        model.stopPlayback();
        javafx.application.Platform.exit();
    }

    private void onVideoSizeChanged(int width, int height) {
        videoSizeChangedHandler.accept(width, height);
    }

    private void onFrameReady(Image image) {
        view.setImage(image);
        
        if (image == null || !subtitlesEnabled) {
            view.showSubtitle("");
            return;
        }

        long currentUs = model.getCurrentTimestampUs();

        for (SubtitleCue subtitle : subtitles) {
            if (currentUs >= subtitle.startUs()
                    && currentUs < subtitle.endUs()) {
                view.showSubtitle(subtitle.text());
                return;
            }
        }

        view.showSubtitle("");
    }

    private void onStatusChanged(String status) {
        view.setStatus(status);
    }

    private void onPlaybackStateChanged(boolean playing, boolean paused) {
        if (playing && !paused) {
            view.showPauseAction();
        } else {
            view.showPlayAction();
        }
    }

    private void onAudioOutputStateChanged(boolean audioOutputEnabled) {
        view.showAudioEnabled(audioOutputEnabled);
    }
}