package comp3011;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.bytedeco.javacv.Frame;

/**
 * Processes video frames using a pool of worker threads.
 * Keeps completed frames in their original submission order.
 */

// Claude was consulted for robustness checks around task cancellation
// and resource cleanup.
public final class VideoProcessor<T> implements AutoCloseable {

    private final ExecutorService executor;
    private final Queue<FrameTask> pending = new ArrayDeque<>();
    private final int capacity;
    private final List<Character> requestedEffects;
    private final ThreadLocal<List<FrameProcessor>> workerProcessors;
    private final ThreadLocal<Boolean> workerInitialised =
            ThreadLocal.withInitial(() -> false);

    private boolean closed;

    public VideoProcessor(int workerCount, List<Character> requestedEffects) {
        if (workerCount < 1) {
            throw new IllegalArgumentException(
                    "Worker count must be positive");
        }

        this.requestedEffects = List.copyOf(requestedEffects);
        
        // Allow several frames to be queued per worker without letting
        // the processing queue grow indefinitely.
        this.capacity = workerCount * 3;
        this.executor = Executors.newFixedThreadPool(workerCount);

        // Each worker gets its own effect processors to avoid sharing mutable
        // effect state between threads.
        workerProcessors = ThreadLocal.withInitial(() -> {
            List<FrameProcessor> processors = new ArrayList<>();
            int[] instanceCounts = new int[128];

            for (char effect : this.requestedEffects) {
                int index = instanceCounts[effect]++;
                processors.add(createFrameProcessor(effect, index));
            }

            return processors;
        });
    }

    private FrameProcessor createFrameProcessor(char effect, int index) {
        return switch (effect) {
            case 'n' -> new FrameNumberer();
            case 's' -> new FrameScratcher(index);
            case 'f' -> new FrameFlickerer(index);
            case 'w' -> new FrameBlackAndWhiter();
            case 'y' -> new FrameYellower();
            case 'v' -> new FrameVignetter();
            case 'd' -> new FrameDuster(index);
            case 'j' -> new FrameJitterer(index);
            case 'm' -> new FrameMottler(index);
            case 'b' -> new FrameBleeder();
            case 'p' -> new FramePepperer(index);

            default -> throw new IllegalArgumentException(
                    "Unknown frame processor: " + effect);
        };
    }

    public void submitFrame(
            Frame frame,
            InfoFrame info,
            InfoVideo videoInfo,
            Function<Frame, T> resultConverter) {

        if (!canSubmit()) {
            throw new IllegalStateException(
                    "Video processing queue is full or closed");
        }

        // The decoder can reuse its buffers, so each task gets its own copy.
        Frame copy = frame.clone();
        FrameTask task;

        try {
            task = new FrameTask(copy, info, videoInfo, resultConverter);
        } catch (RuntimeException e) {
            copy.close();
            throw e;
        }

        pending.add(task);

        try {
            executor.execute(task);
        } catch (RuntimeException e) {
            pending.remove(task);
            task.discard();
            throw e;
        }
    }

    private void processFrame(
            Frame frame,
            InfoFrame info,
            InfoVideo videoInfo) throws Exception {

        List<FrameProcessor> processors = workerProcessors.get();

        // Initialise each worker's processors once, rather than for eve
        if (!workerInitialised.get()) {
            for (FrameProcessor processor : processors) {
                processor.initialise(videoInfo);
            }

            workerInitialised.set(true);
        }

        for (FrameProcessor processor : processors) {
            processor.process(frame, info);
        }
    }

    private final class FrameTask extends FutureTask<T> {

        private final Frame frame;
        private final AtomicBoolean started = new AtomicBoolean();
        private final AtomicBoolean released = new AtomicBoolean();

        FrameTask(
                Frame frame,
                InfoFrame info,
                InfoVideo videoInfo,
                Function<Frame, T> converter) {

            super(() -> {
                processFrame(frame, info, videoInfo);
                return converter.apply(frame);
            });

            this.frame = frame;
        }

        // Ensure the native frame is released only once, even if the
        // task is cancelled while processing is taking place.
        private void releaseFrame() {
            if (released.compareAndSet(false, true)) {
                frame.close();
            }
        }

        @Override
        public void run() {
            if (!started.compareAndSet(false, true)) {
                return;
            }

            try {
                super.run();
            } finally {
                releaseFrame();
            }
        }

        void discard() {
            if (started.compareAndSet(false, true)) {
                cancel(false);
                releaseFrame();
            } else {
                cancel(false);
            }
        }
    }

    public boolean canSubmit() {
        return !closed && pending.size() < capacity;
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public int pendingCount() {
        return pending.size();
    }

    // Workers may finish processing out of order, but frames are
    // returned in submission order to preserve correct playback.
    public T pollReady() throws Exception {
        FrameTask next = pending.peek();

        if (next == null || !next.isDone()) {
            return null;
        }

        pending.remove();

        try {
            return next.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();

            if (cause instanceof Exception exception) {
                throw exception;
            }

            if (cause instanceof Error error) {
                throw error;
            }

            throw new RuntimeException(cause);
        } catch (CancellationException e) {
            throw new IllegalStateException(
                    "Frame processing was cancelled", e);
        }
    }

    // Cancel outstanding tasks and clear the pending queue when
    // processing is no longer needed, such as after a seek or stop.
    public void cancelPending() {
        for (FrameTask task : pending) {
            task.discard();
        }

        pending.clear();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        cancelPending();
        executor.shutdownNow();
    }
}
