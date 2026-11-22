# COMP3011 Cloud and Concurrent Programming — Assignment 3

A multithreaded JavaFX video player with subtitles generated through a Spring Boot speech-to-text server and OpenAI Whisper.

Assignment 3 combines the projects from Assignments 1 and 2, adding timestamped subtitles rendered directly onto video frames without interrupting playback.

## Project Structure

- **`web-server/` (Assignment 1):** A Spring Boot speech-to-text website and REST API. Originally developed to record audio through a browser and transcribe it using OpenAI's `gpt-4o-mini-transcribe` model. Extended in Assignment 3 to provide timestamped subtitles using `whisper-1`.

- **`video-player/` (Assignment 2):** Based on a university-provided single-threaded JavaFX video player with playback controls and visual effects. Extended with command-line effect selection and multithreaded frame processing in Assignment 2, followed by subtitle support in Assignment 3.

### Code Attribution

The original video-player starter code was supplied by the course (credited in source headers to Simon Ratcliffe, in collaboration with GPT-5.6 Terra).

This repository contains my modifications and additions across the three assignments, rather than an entirely from-scratch implementation. 

## Requirements

- Java JDK (tested with Java 25)
- Apache Maven
- FFmpeg installed separately and available on the system `PATH`
- Internet access for new transcriptions
- An OpenAI API key, provided through `OPENAI_API_KEY`

The video player uses **JavaCV's FFmpeg bindings to decode video**, but uses the **system-installed FFmpeg executable to extract audio** for subtitle transcription. The audio extractor launches FFmpeg as an external process, so the `ffmpeg` command must be available on `PATH`.

On Ubuntu or Pop!_OS, install and verify FFmpeg with:

```bash
sudo apt update
sudo apt install ffmpeg
ffmpeg -version
```

## Build and Run

### 1. Start the Web Server

From `web-server/`:

```bash
./mvnw clean package -DskipTests
read -rsp "OpenAI API key: " OPENAI_API_KEY; echo
export OPENAI_API_KEY
./mvnw spring-boot:run
```

The server runs at `http://localhost:8080`.

### 2. Start the Video Player

From `video-player/`, in a separate terminal:

```bash
mvn clean package
mvn javafx:run '-Djavafx.args=/path/to/video.mp4 -a -S'
```

Replace `/path/to/video.mp4` with the absolute path to your video file.

- `-a` enables audio.
- `-S` enables subtitles at startup.
- Press **S** or use the subtitle button to toggle subtitles during playback.

## Video Effects

Effects are enabled through command-line flags. They are applied in the order specified and can be repeated.

| Flag | Effect |
|------|--------|
| `-n` | Frame numbers |
| `-s` | Scratches |
| `-f` | Flicker |
| `-w` | Black and white |
| `-y` | Warm/yellow tint |
| `-v` | Vignette |
| `-d` | Dust |
| `-j` | Jitter |
| `-m` | Mottle |
| `-b` | Light bleed |
| `-p` | Pepper marks |
| `-S` | Subtitles |

### Apply Effects

Apply scratches, flicker and vignette:

```bash
mvn javafx:run '-Djavafx.args=/path/to/video.mp4 -a -S -s -f -v'
```

### Remove Effects

To remove scratches, omit `-s` from the command:

```bash
mvn javafx:run '-Djavafx.args=/path/to/video.mp4 -a -S -f -v'
```

Changes to effect flags take effect when the player is relaunched.

### Run Without Effects

```bash
mvn javafx:run '-Djavafx.args=/path/to/video.mp4 -a'
```

View all available command-line options:

```bash
mvn javafx:run '-Djavafx.args=--help'
```

## Subtitles

When enabled, the player extracts audio in 15-second chunks and sends it to the Spring Boot server, which uses OpenAI's `whisper-1` model to generate timestamped transcriptions.

Returned text and timestamps are stored in memory and rendered onto matching video frames. Transcription runs on a background thread to avoid interrupting playback.

If a transcription request temporarily fails, the player retries the same chunk at four-second intervals, up to 30 attempts. Previously generated subtitles remain available during network interruptions.

Transcription is sequential, so subtitles for later sections may not be available immediately. Persistent subtitle caching is not implemented yet.