/*
 * Starter code supplied for Adelaide University COMP3011 Assignment 2.
 * Students are free to modify this file for assessment purposes.
 * 
 * Authors:
 *   1. Simon Ratcliffe, in collaboration with GPT-5.6 Terra
 *   2. Suhanna Singh - a2960347
 *
 * Copyright 2026 Simon Ratcliffe
 */
package comp3011;

import java.io.File;
import java.util.function.BiConsumer;
import java.util.List;
import java.util.ArrayList;

import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

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
    private File currentVideoFile;
    private boolean subtitleGenerationStarted;
    
    public VideoPlayerController(
            boolean audioEnabled,
            boolean subtitlesEnabled,
            List<Character> requestedEffects,
            BiConsumer<Integer, Integer> videoSizeChangedHandler) {
        this.view = new VideoPlayerView();
        this.videoSizeChangedHandler = videoSizeChangedHandler;
        model = new VideoPlayerModel(
                audioEnabled,
                requestedEffects,
                this::onVideoSizeChanged,
                this::onFrameReady,
                this::onStatusChanged,
                this::onPlaybackStateChanged,
                this::onAudioOutputStateChanged);
        
        model.getSubtitleState().setEnabled(subtitlesEnabled);

        view.getStartOverButton().setOnAction(_ -> startOver());
        view.getBackFiveButton().setOnAction(_ -> backFiveSeconds());
        view.getPausePlayButton().setOnAction(_ -> togglePause());
        view.getForwardFiveButton().setOnAction(_ -> forwardFiveSeconds());
        view.getStopButton().setOnAction(_ -> stopAndExit());
        view.getAudioButton().setOnAction(_ -> toggleAudioOutput());
        view.showAudioEnabled(model.isAudioOutputEnabled());
        view.getSubtitleButton().setOnAction(_ -> toggleSubtitles());
        view.showSubtitlesEnabled(subtitlesEnabled);
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
            } else if (event.getCode() == KeyCode.S) {
                toggleSubtitles();
                event.consume();
            }
        });
    }

    public VideoPlayerView getView() {
        return view;
    }

    public void play(File file) {
        currentVideoFile = file;
        subtitleGenerationStarted = false;
        model.getSubtitleState().clear();

        view.setPlaybackControlsDisabled(false);
        model.play(file);

        if (model.getSubtitleState().isEnabled()) {
            startSubtitleGeneration();
        }
    }

    private void startSubtitleGeneration() {
        if (currentVideoFile == null || subtitleGenerationStarted) {
            return;
        }

        subtitleGenerationStarted = true;

        subtitleGenerator.generate(currentVideoFile, segments -> {
            List<SubtitleState.Cue> frameCues = new ArrayList<>();
            for (var subtitle : segments) {
                if (subtitle.text().isBlank()
                        || subtitle.endUs() <= subtitle.startUs()) {
                    continue;
                }

                // Share the same timed subtitle with the frame renderer.
                frameCues.add(new SubtitleState.Cue(
                        subtitle.startUs(),
                        subtitle.endUs(),
                        subtitle.text()
                ));

                System.out.printf("[%.2fs - %.2fs] %s%n",
                        subtitle.startUs() / 1_000_000.0,
                        subtitle.endUs() / 1_000_000.0,
                        subtitle.text());
            }

            // Publish the subtitles to the frame-processing threads.
            model.getSubtitleState().addCues(frameCues);

            System.out.println("Stored subtitles: " + frameCues.size());

        }).exceptionally(error -> {
            System.err.println("Subtitle generation failed: " + error.getMessage());

             // Allow another transcription attempt after a failed request.
            javafx.application.Platform.runLater(() -> {
                subtitleGenerationStarted = false;
            });

            return null;
        });
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

    private void toggleSubtitles() {
        boolean enabled = !model.getSubtitleState().isEnabled();
        model.getSubtitleState().setEnabled(enabled);
        view.showSubtitlesEnabled(enabled);

        if (enabled) {
            startSubtitleGeneration();
        } 
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