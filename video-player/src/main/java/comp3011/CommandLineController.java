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

// Claude was consulted when checking command-line edge cases
// and exit-code handling.
package comp3011;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created in the VideoPlayerApp's main function to receive and parse the video player's command-line arguments.
 *
 * <p>
 * Extracts the video file and supported launch options, reports invalid input
 * or help text, and exposes the resulting launch configuration via methods to the
 * {@link VideoPlayerApp}.
 * </p>
 */
public class CommandLineController {
    private final String[] args;

    private boolean helpRequested;
    private boolean otherOptionsGiven;
    private boolean audioRequested;
    private boolean maximiseRequested;
    private Integer displayId;
    private File videoFile;
    private String errorMessage;
    private final List<Character> requestedEffects = new ArrayList<>();

    public CommandLineController(String[] args) {
        this.args = args.clone();
        parse();
        if (errorMessage != null) {
            System.out.println(errorMessage);
        }
        if (helpRequested) {
            printHelp();
        }
    }

    public File getVideoFile() {
        return videoFile;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getExitCode() {
        return errorMessage == null ? 0 : 1;
    }
    
    public List<Character> getRequestedEffects() {
        return List.copyOf(requestedEffects);
    }

    public Integer getDisplayId() {
        return displayId;
    }

    public boolean isAudioRequested() {
        return audioRequested;
    }

    public boolean isMaximiseRequested() {
        return maximiseRequested;
    }

    // Only launch when the arguments are valid, a video file exists,
    // and help has not been requested.
    public boolean shouldLaunchApplication() {
        return errorMessage == null && !helpRequested && videoFile != null;
    }

    private void parse() {
    	for (String arg : args) {
            if (arg.equals("--help")
                    || (arg.startsWith("-")
                        && !arg.startsWith("--")
                        && arg.indexOf('h', 1) >= 0)) {
                helpRequested = true;
            }
        }
    	
        List<String> videoFiles = new ArrayList<>();
        for (String arg : args) {
        	if (arg.startsWith("--")) {
                parseLongOption(arg);
            } else if (arg.startsWith("-") && arg.length() > 1) {
                parseShortOptions(arg);
            } else {
                videoFiles.add(arg);
            }

            if (errorMessage != null) {
                return;
            }
        }
        	
        if (videoFiles.size() > 1) {
            errorMessage = "Usage: VideoPlayer [options] [video-file]";
        } else if (videoFiles.size() == 1) {
            videoFile = new File(videoFiles.get(0));

            if (!videoFile.isFile()) {
                errorMessage = "File not found: " + videoFile.getPath();
                videoFile = null;
            }
        } else if (!helpRequested || otherOptionsGiven) {
            errorMessage = "No video file specified.";
        }
    }
    
    

	private void parseLongOption(String arg) {
	    switch (arg) {
	        case "--help" -> helpRequested = true;
	        case "--audio" -> option(() -> audioRequested = true);
	        case "--maximise" -> option(() -> maximiseRequested = true);
	        case "--monitor-1" -> option(() -> setDisplayId(1));
	        case "--monitor-2" -> option(() -> setDisplayId(2));
	
	        case "--number-frames" -> option(() -> requestedEffects.add('n'));
	        case "--scratch-frames" -> option(() -> requestedEffects.add('s'));
	        case "--flicker-frames" -> option(() -> requestedEffects.add('f'));
	        case "--black-and-white" -> option(() -> requestedEffects.add('w'));
	        case "--yellow-frames" -> option(() -> requestedEffects.add('y'));
	        case "--vignette-frames" -> option(() -> requestedEffects.add('v'));
	        case "--dust-frames" -> option(() -> requestedEffects.add('d'));
	        case "--jitter-frames" -> option(() -> requestedEffects.add('j'));
	        case "--mottle-frames" -> option(() -> requestedEffects.add('m'));
	        case "--bleed-frames" -> option(() -> requestedEffects.add('b'));
	        case "--pepper-frames" -> option(() -> requestedEffects.add('p'));
	
	        default -> errorMessage = "Unknown option: " + arg;
	    }
	}


	private void parseShortOptions(String arg) {
	    for (int i = 1; i < arg.length(); i++) {
	        char option = arg.charAt(i);
	
	        switch (option) {
	            case 'h' -> helpRequested = true;
	            case 'a' -> option(() -> audioRequested = true);
	            case 'x' -> option(() -> maximiseRequested = true);
	            case '1' -> option(() -> setDisplayId(1));
	            case '2' -> option(() -> setDisplayId(2));
	
	            case 'n', 's', 'f', 'w', 'y', 'v',
	                 'd', 'j', 'm', 'b', 'p' ->
	                    option(() -> requestedEffects.add(option));
	
	            default -> {
	                errorMessage = "Unknown option: -" + option;
	                return;
	            }
	        }
	
	        if (errorMessage != null) {
	            return;
	        }
	    }
	}


    private void setDisplayId(int displayId) {
        if (this.displayId != null && this.displayId != displayId) {
            errorMessage = "Only one display option can be used";
            return;
        }
        this.displayId = displayId;
    }

    private void printHelp() {
        System.out.println("Usage: VideoPlayer [options] [video-file]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -h, --help         Show this help message");
        System.out.println("  -a, --audio        Play audio");
        System.out.println("  -x, --maximise     Open the player maximised");
        System.out.println("  -1, --monitor-1    Open the player on display 1");
        System.out.println("  -2, --monitor-2    Open the player on display 2");
        
        System.out.println();
        System.out.println("Frame processors:");
        System.out.println("  -n, --number-frames    Render the frame number onto each frame");
        System.out.println("  -s, --scratch-frames   Render vertical film scratches");
        System.out.println("  -f, --flicker-frames   Randomly dim frames");
        System.out.println("  -w, --black-and-white  Convert frames to black and white");
        System.out.println("  -y, --yellow-frames    Apply a warmer colour temperature");
        System.out.println("  -v, --vignette-frames  Darken the frame edges");
        System.out.println("  -d, --dust-frames      Render dust and hair marks");
        System.out.println("  -j, --jitter-frames    Randomly displace frames by a few pixels");
        System.out.println("  -m, --mottle-frames    Add cloudy emulsion mottling");
        System.out.println("  -b, --bleed-frames     Bleed light into frames");
        System.out.println("  -p, --pepper-frames    Pepper frames with dark spots/blotches");

        System.out.println();
        System.out.println("Frame processors are applied in command-line order and may be repeated.");
        System.out.println("Example: -nssnfwyvdjmbp numbers, scratches twice, numbers again, flickers,");
        System.out.println("converts, warms, vignettes, dusts, jitters, mottles, bleeds, then peppers.");
        }
    
        // Track whether an option other than help was provided so that
        // missing video files are treated as errors.
	    private void option(Runnable action) {
	        otherOptionsGiven = true;
	        action.run();
	    }
}
