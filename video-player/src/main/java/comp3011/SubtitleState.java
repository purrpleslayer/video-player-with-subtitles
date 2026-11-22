package comp3011;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Shares subtitle data between the controller and frame processors.
public final class SubtitleState {

    public record Cue(long startUs, long endUs, String text) {}

    private volatile boolean enabled;
    private volatile List<Cue> cues = List.of();

    public SubtitleState(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public synchronized void clear() {
        cues = List.of();
    }

    // Add subtitle batches while keeping the list sorted by start time.
    public synchronized void addCues(List<Cue> newCues) {
        List<Cue> updated = new ArrayList<>(cues);
        updated.addAll(newCues);
        updated.sort(Comparator.comparingLong(Cue::startUs));
        cues = List.copyOf(updated);
    }

    // Return the subtitle matching the current video timestamp.
    public String textAt(long timestampUs) {
        if (!enabled) {
            return "";
        }

        for (Cue cue : cues) {
            if (timestampUs >= cue.startUs()
                    && timestampUs < cue.endUs()) {
                return cue.text();
            }
        }

        return "";
    }
}