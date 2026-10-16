package com.laglens.capture;

import com.laglens.analysis.Finding;
import com.laglens.metrics.MetricsSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounded user-driven recording of diagnostics.
 *
 * <p>Workflow: start (LIGHT or DETAILED) → reproduce the issue → stop manually
 * or let the configured duration elapse. Snapshots are appended at ~1 Hz and
 * hard-capped ({@link #MAX_SNAPSHOTS}) so a forgotten recording cannot grow
 * without bound. The rolling pre-buffer is supplied by the live collectors at
 * export time rather than duplicated here.
 */
public final class CaptureSession {
    public enum Mode {
        LIGHT,
        DETAILED
    }

    public enum State {
        IDLE,
        RECORDING
    }

    /** Absolute cap: ~10 minutes at 1 Hz. */
    public static final int MAX_SNAPSHOTS = 600;

    private State state = State.IDLE;
    private Mode mode = Mode.LIGHT;
    private long startedAtMs;
    private long autoStopAtMs = -1L;
    private final List<MetricsSnapshot> snapshots = new ArrayList<>();
    private List<Finding> closingFindings = Collections.emptyList();
    private int droppedSnapshots;

    public synchronized boolean start(Mode mode, long nowMs, long autoStopAfterMs) {
        if (state == State.RECORDING) {
            return false;
        }
        this.mode = mode;
        this.startedAtMs = nowMs;
        this.autoStopAtMs = autoStopAfterMs > 0 ? nowMs + autoStopAfterMs : -1L;
        this.snapshots.clear();
        this.closingFindings = Collections.emptyList();
        this.droppedSnapshots = 0;
        this.state = State.RECORDING;
        return true;
    }

    public synchronized void record(MetricsSnapshot snapshot) {
        if (state != State.RECORDING) {
            return;
        }
        if (snapshots.size() >= MAX_SNAPSHOTS) {
            droppedSnapshots++;
            return;
        }
        snapshots.add(snapshot);
    }

    /** Returns true when the auto-stop deadline has passed (checked on tick). */
    public synchronized boolean checkAutoStop(long nowMs) {
        return state == State.RECORDING && autoStopAtMs > 0 && nowMs >= autoStopAtMs;
    }

    public synchronized CaptureResult stop(List<Finding> findings) {
        if (state != State.RECORDING) {
            return null;
        }
        state = State.IDLE;
        closingFindings = findings == null ? Collections.emptyList() : List.copyOf(findings);
        return new CaptureResult(mode, startedAtMs, System.currentTimeMillis(),
                List.copyOf(snapshots), closingFindings, droppedSnapshots);
    }

    public synchronized State state() {
        return state;
    }

    public synchronized Mode mode() {
        return mode;
    }

    public synchronized long startedAtMs() {
        return startedAtMs;
    }

    public synchronized int snapshotCount() {
        return snapshots.size();
    }

    /** Finished capture handed to the report writer. */
    public record CaptureResult(Mode mode, long startedAtMs, long stoppedAtMs,
            List<MetricsSnapshot> snapshots, List<Finding> findings, int droppedSnapshots) {
        public long durationMs() {
            return stoppedAtMs - startedAtMs;
        }
    }
}
