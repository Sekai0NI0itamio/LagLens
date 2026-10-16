package com.laglens.metrics;

/**
 * Classifies frame-time spikes against configurable thresholds.
 *
 * <p>A single slow frame is normal (shader compile, disk hitch, OS pause), so
 * an isolated spike is reported as {@link SpikeSeverity#ISOLATED} and only
 * recurring spikes raise the severity. Thresholds come from
 * {@link com.laglens.config.LagLensConfig} so they can be tuned per machine.
 */
public final class SpikeDetector {

    public enum SpikeSeverity {
        NONE,
        ISOLATED,
        RECURRING,
        SEVERE
    }

    private SpikeDetector() {
    }

    /** Counts samples strictly above the threshold. */
    public static int countAbove(double[] samples, double thresholdMs) {
        if (samples == null) {
            return 0;
        }
        int count = 0;
        for (double v : samples) {
            if (v > thresholdMs) {
                count++;
            }
        }
        return count;
    }

    /**
     * Classifies a window of frame times.
     *
     * @param samples          frame times in ms
     * @param warnMs           threshold for a noteworthy spike
     * @param severeMs         threshold for a severe spike
     * @param recurringMinimum how many warn-level spikes make the problem recurring
     */
    public static SpikeSeverity classify(double[] samples, double warnMs, double severeMs,
            int recurringMinimum) {
        if (samples == null || samples.length == 0) {
            return SpikeSeverity.NONE;
        }
        int severe = countAbove(samples, severeMs);
        if (severe > 0) {
            return SpikeSeverity.SEVERE;
        }
        int warn = countAbove(samples, warnMs);
        if (warn == 0) {
            return SpikeSeverity.NONE;
        }
        if (warn >= Math.max(2, recurringMinimum)) {
            return SpikeSeverity.RECURRING;
        }
        return SpikeSeverity.ISOLATED;
    }

    /** Spikes per minute over the given window length in seconds. */
    public static double spikesPerMinute(int spikeCount, double windowSeconds) {
        if (windowSeconds <= 0.0) {
            return Double.NaN;
        }
        return spikeCount * 60.0 / windowSeconds;
    }
}
