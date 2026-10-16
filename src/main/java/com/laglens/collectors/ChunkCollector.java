package com.laglens.collectors;

import com.laglens.metrics.Clock;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Client chunk observatory: loaded-chunk gauge plus load/unload event timeline.
 *
 * <p>What is directly measured: the client chunk-manager loaded count and the
 * Fabric {@code ClientChunkEvents} load/unload callbacks. What is NOT claimed:
 * server-side generation cost (invisible to the client), mesh-rebuild CPU time
 * (no public hook without renderer-specific integration), and upload timing
 * (not exposed by the vanilla pipeline). Those stages are reported as
 * unavailable with an explanation, never inferred.
 */
public final class ChunkCollector {
    private static final long EVENT_HORIZON_MS = 120_000L;

    private final Deque<Long> loadEvents = new ArrayDeque<>();
    private final Deque<Long> unloadEvents = new ArrayDeque<>();
    private int loadedChunks = -1;
    private boolean available;

    public synchronized void recordLoad() {
        loadEvents.addLast(Clock.milliTime());
        evict();
    }

    public synchronized void recordUnload() {
        unloadEvents.addLast(Clock.milliTime());
        evict();
    }

    private void evict() {
        long cutoff = Clock.milliTime() - EVENT_HORIZON_MS;
        while (!loadEvents.isEmpty() && loadEvents.getFirst() < cutoff) {
            loadEvents.removeFirst();
        }
        while (!unloadEvents.isEmpty() && unloadEvents.getFirst() < cutoff) {
            unloadEvents.removeFirst();
        }
    }

    public synchronized void setLoadedChunks(int count) {
        this.loadedChunks = count;
        this.available = true;
    }

    public synchronized void markUnavailable() {
        this.available = false;
        this.loadedChunks = -1;
    }

    public synchronized int loadedChunks() {
        return loadedChunks;
    }

    public synchronized boolean available() {
        return available;
    }

    /** Events per minute over the trailing 2 minutes. -1 when no data. */
    public synchronized int eventsPerMinute() {
        evict();
        if (!available && loadEvents.isEmpty() && unloadEvents.isEmpty()) {
            return -1;
        }
        return (loadEvents.size() + unloadEvents.size()) / 2;
    }

    /** Chunk events within {@code withinMs} before {@code timestampMs}. */
    public synchronized int eventsNear(long timestampMs, long withinMs) {
        int count = 0;
        for (long t : loadEvents) {
            if (Math.abs(timestampMs - t) <= withinMs) {
                count++;
            }
        }
        for (long t : unloadEvents) {
            if (Math.abs(timestampMs - t) <= withinMs) {
                count++;
            }
        }
        return count;
    }

    public synchronized void reset() {
        loadEvents.clear();
        unloadEvents.clear();
        loadedChunks = -1;
        available = false;
    }
}
