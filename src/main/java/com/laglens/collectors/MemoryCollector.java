package com.laglens.collectors;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;

/**
 * Heap and garbage-collection collector based on {@link MemoryMXBean} and
 * {@link GarbageCollectorMXBean}.
 *
 * <p>Sampled at the configured tick cadence (never per frame). Reports used,
 * committed and max heap plus GC count/time deltas between samples. A single
 * high heap reading is reported as-is; the diagnostics engine only warns
 * about sustained post-GC growth, never a one-off value, because high usage
 * alone is not evidence of a leak.
 */
public final class MemoryCollector {
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

    private long heapUsedBytes = -1L;
    private long heapCommittedBytes = -1L;
    private long heapMaxBytes = -1L;
    private long totalGcCount = -1L;
    private long totalGcTimeMs = -1L;
    private boolean available;

    /** Last completed GC pause observed, in ms (-1 when unknown). */
    private long lastGcPauseMs = -1L;
    private long lastGcPauseAtMs = -1L;

    public synchronized void sample(long nowMs) {
        try {
            MemoryUsage heap = memory.getHeapMemoryUsage();
            heapUsedBytes = heap.getUsed();
            heapCommittedBytes = heap.getCommitted();
            heapMaxBytes = heap.getMax();
            long count = 0L;
            long time = 0L;
            for (GarbageCollectorMXBean bean : gcBeans) {
                long c = bean.getCollectionCount();
                long t = bean.getCollectionTime();
                if (c >= 0) {
                    count += c;
                }
                if (t >= 0) {
                    time += t;
                }
            }
            // Detect a newly completed collection to timestamp GC overlap checks.
            if (totalGcCount >= 0 && count > totalGcCount) {
                lastGcPauseMs = time - totalGcTimeMs;
                lastGcPauseAtMs = nowMs;
            }
            totalGcCount = count;
            totalGcTimeMs = time;
            available = true;
        } catch (Exception e) {
            available = false;
        }
    }

    public synchronized boolean available() {
        return available;
    }

    public synchronized long heapUsedBytes() {
        return heapUsedBytes;
    }

    public synchronized long heapCommittedBytes() {
        return heapCommittedBytes;
    }

    public synchronized long heapMaxBytes() {
        return heapMaxBytes;
    }

    public synchronized long totalGcCount() {
        return totalGcCount;
    }

    public synchronized long totalGcTimeMs() {
        return totalGcTimeMs;
    }

    public synchronized long lastGcPauseMs() {
        return lastGcPauseMs;
    }

    public synchronized long lastGcPauseAtMs() {
        return lastGcPauseAtMs;
    }

    public synchronized void reset() {
        heapUsedBytes = -1L;
        heapCommittedBytes = -1L;
        heapMaxBytes = -1L;
        totalGcCount = -1L;
        totalGcTimeMs = -1L;
        lastGcPauseMs = -1L;
        lastGcPauseAtMs = -1L;
        available = false;
    }
}
