package com.laglens.metrics;

/**
 * Central monotonic clock for elapsed-time measurements.
 *
 * <p>All durations in LagLens are measured in nanoseconds from
 * {@link System#nanoTime()}, never from wall-clock time, so NTP adjustments
 * and daylight-saving changes cannot corrupt frame-time or GC-overlap math.
 */
public final class Clock {
    private Clock() {
    }

    /** Monotonic timestamp in nanoseconds. */
    public static long nanoTime() {
        return System.nanoTime();
    }

    /** Monotonic timestamp in whole milliseconds. */
    public static long milliTime() {
        return System.nanoTime() / 1_000_000L;
    }

    /** Converts a nanosecond duration to milliseconds as a double. */
    public static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }
}
