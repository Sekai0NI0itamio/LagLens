package com.laglens.metrics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Bounded timestamped sample series with explicit missing-data markers.
 *
 * <p>Samples older than the configured horizon are evicted on insert, so the
 * series can never grow without bound. A {@code null} value records that a
 * sample was expected but unavailable (player in a menu, collector disabled),
 * which is reported as missing rather than interpolated or counted as zero.
 */
public final class TimeSeries {
    /** Single sample; {@code value} is null when the sample is missing. */
    public record Sample(long timestampMs, Double value) {
    }

    private final Deque<Sample> samples = new ArrayDeque<>();
    private final long horizonMs;
    private final int maxSamples;

    public TimeSeries(long horizonMs, int maxSamples) {
        if (horizonMs <= 0 || maxSamples <= 0) {
            throw new IllegalArgumentException("horizon and maxSamples must be positive");
        }
        this.horizonMs = horizonMs;
        this.maxSamples = maxSamples;
    }

    public synchronized void add(long timestampMs, Double value) {
        samples.addLast(new Sample(timestampMs, value));
        evict(timestampMs);
    }

    private void evict(long nowMs) {
        while (samples.size() > maxSamples) {
            samples.removeFirst();
        }
        while (!samples.isEmpty() && nowMs - samples.getFirst().timestampMs() > horizonMs) {
            samples.removeFirst();
        }
    }

    /** Present (non-missing) values inside [nowMs - windowMs, nowMs]. */
    public synchronized double[] presentValues(long nowMs, long windowMs) {
        List<Double> out = new ArrayList<>();
        for (Sample s : samples) {
            if (nowMs - s.timestampMs() <= windowMs && s.value() != null) {
                out.add(s.value());
            }
        }
        double[] arr = new double[out.size()];
        for (int i = 0; i < out.size(); i++) {
            arr[i] = out.get(i);
        }
        return arr;
    }

    /** Counts missing markers inside the window. */
    public synchronized int missingCount(long nowMs, long windowMs) {
        int count = 0;
        for (Sample s : samples) {
            if (nowMs - s.timestampMs() <= windowMs && s.value() == null) {
                count++;
            }
        }
        return count;
    }

    public synchronized int size() {
        return samples.size();
    }

    public synchronized void clear() {
        samples.clear();
    }
}
