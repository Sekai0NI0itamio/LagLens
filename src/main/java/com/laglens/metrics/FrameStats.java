package com.laglens.metrics;

/**
 * Pure statistics over a sample window: average, percentiles, min, max.
 *
 * <p>All methods treat an empty input as "no data" and return
 * {@link Double#NaN} rather than zero, so the UI can render an honest
 * unavailable state instead of a misleading zero.
 */
public final class FrameStats {
    private FrameStats() {
    }

    public static double average(double[] samples) {
        if (samples == null || samples.length == 0) {
            return Double.NaN;
        }
        double sum = 0.0;
        for (double v : samples) {
            sum += v;
        }
        return sum / samples.length;
    }

    /**
     * Percentile with linear interpolation. {@code p} is in [0, 100].
     * Expects an unsorted array; sorts a copy internally.
     */
    public static double percentile(double[] samples, double p) {
        if (samples == null || samples.length == 0) {
            return Double.NaN;
        }
        if (p < 0.0 || p > 100.0) {
            throw new IllegalArgumentException("percentile must be in [0, 100]");
        }
        double[] sorted = samples.clone();
        java.util.Arrays.sort(sorted);
        if (sorted.length == 1) {
            return sorted[0];
        }
        double rank = (p / 100.0) * (sorted.length - 1);
        int low = (int) Math.floor(rank);
        int high = (int) Math.ceil(rank);
        if (low == high) {
            return sorted[low];
        }
        double frac = rank - low;
        return sorted[low] + frac * (sorted[high] - sorted[low]);
    }

    public static double min(double[] samples) {
        if (samples == null || samples.length == 0) {
            return Double.NaN;
        }
        double m = samples[0];
        for (double v : samples) {
            if (v < m) {
                m = v;
            }
        }
        return m;
    }

    public static double max(double[] samples) {
        if (samples == null || samples.length == 0) {
            return Double.NaN;
        }
        double m = samples[0];
        for (double v : samples) {
            if (v > m) {
                m = v;
            }
        }
        return m;
    }

    /** Converts an average frame time in ms to FPS; NaN when input is not finite. */
    public static double frameMsToFps(double avgFrameMs) {
        if (!Double.isFinite(avgFrameMs) || avgFrameMs <= 0.0) {
            return Double.NaN;
        }
        return 1000.0 / avgFrameMs;
    }
}
