package com.laglens.collectors;

import com.laglens.config.LagLensConfig;
import com.laglens.metrics.Clock;
import com.laglens.metrics.FrameStats;
import com.laglens.metrics.RingBuffer;
import com.laglens.metrics.SpikeDetector;

/**
 * Frame-time collector.
 *
 * <p>Source: called once per rendered frame from the Fabric HUD-render event,
 * which fires inside the client's render pass. The measured interval is the
 * time between consecutive HUD-render callbacks (i.e. the render-loop period
 * as observed on the HUD pass), timestamped with {@link System#nanoTime()}.
 * This is a direct measurement of achieved frame pacing, not a GPU timer and
 * not vsync state. When no world is loaded (title screen) no samples arrive
 * and the dashboard reports "no samples" instead of inventing values.
 *
 * <p>Cost per frame: one nanotime read, one ring write, occasional min/max.
 * No allocation on the hot path ({@link #snapshot()} copies only on demand).
 */
public final class FrameTimeCollector {
    private final RingBuffer frames;
    private long lastFrameNanos = -1L;
    private double lastDeltaMs = Double.NaN;
    private int currentFps = -1;

    public FrameTimeCollector(LagLensConfig config) {
        // ~70 samples/sec worst case over the full history window, capped to bound memory.
        int capacity = Math.min(20000, Math.max(600, config.historySeconds * 70));
        this.frames = new RingBuffer(capacity);
    }

    /** Records one frame; call from the render thread only. */
    public synchronized void recordFrame() {
        long now = Clock.nanoTime();
        if (lastFrameNanos != -1L) {
            double deltaMs = Clock.nanosToMillis(now - lastFrameNanos);
            // Discard absurd deltas (suspend/resume, debugger pause) so they do not
            // permanently skew percentiles; they are still counted as single spikes upstream.
            if (deltaMs >= 0.0 && deltaMs < 10_000.0) {
                frames.add(deltaMs);
                lastDeltaMs = deltaMs;
            }
        }
        lastFrameNanos = now;
    }

    public synchronized void setCurrentFps(int fps) {
        this.currentFps = fps;
    }

    public synchronized int currentFps() {
        return currentFps;
    }

    public synchronized int sampleCount() {
        return frames.size();
    }

    public synchronized double[] samples() {
        return frames.toArray();
    }

    public synchronized double average() {
        return FrameStats.average(frames.toArray());
    }

    public synchronized double percentile(double p) {
        return FrameStats.percentile(frames.toArray(), p);
    }

    public synchronized double max() {
        return FrameStats.max(frames.toArray());
    }

    public synchronized double lastDeltaMs() {
        return lastDeltaMs;
    }

    public synchronized int spikeCount(LagLensConfig config) {
        return SpikeDetector.countAbove(frames.toArray(), config.spikeWarnMs);
    }

    public synchronized void reset() {
        frames.clear();
        lastFrameNanos = -1L;
        lastDeltaMs = Double.NaN;
        currentFps = -1;
    }
}
