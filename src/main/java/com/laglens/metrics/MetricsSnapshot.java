package com.laglens.metrics;

/**
 * Typed point-in-time view of every metric LagLens tracks.
 *
 * <p>Units are fixed: frame times in milliseconds, memory in bytes, GC time in
 * milliseconds, ping in milliseconds, server tick in milliseconds. Any metric
 * that could not be measured is flagged with its {@code *Available} boolean
 * and its numeric field is {@link Double#NaN} (or -1 for counts) — never zero,
 * so the UI and reports cannot mistake "unknown" for "none".
 */
public final class MetricsSnapshot {
    public final long timestampMs;
    public final long windowMs;

    // Frame / rendering
    public final double fps;
    public final double avgFrameMs;
    public final double p50FrameMs;
    public final double p95FrameMs;
    public final double p99FrameMs;
    public final double maxFrameMs;
    public final int spikeCount;
    public final int frameSampleCount;

    // Memory / GC (deltas measured over windowMs)
    public final long heapUsedBytes;
    public final long heapCommittedBytes;
    public final long heapMaxBytes;
    public final long gcCountDelta;
    public final double gcTimeMsDelta;
    public final boolean memoryAvailable;

    // Entities
    public final int entityCount;
    public final boolean entityAvailable;
    public final int blockEntityCount;
    public final boolean blockEntityAvailable;

    // Chunks
    public final int loadedChunks;
    public final boolean chunksAvailable;
    public final int chunkEventsPerMin;
    public final int chunkSpikeCoincidences;

    // Network (client-observable only)
    public final double pingMs;
    public final double pingJitterMs;
    public final boolean networkAvailable;

    // Server tick (integrated server or companion only)
    public final double serverTickMs;
    public final boolean serverTickAvailable;
    public final String serverTickSource;

    // GC overlap evidence
    public final int gcSpikeCoincidences;

    public final int installedModCount;

    private MetricsSnapshot(Builder b) {
        this.timestampMs = b.timestampMs;
        this.windowMs = b.windowMs;
        this.fps = b.fps;
        this.avgFrameMs = b.avgFrameMs;
        this.p50FrameMs = b.p50FrameMs;
        this.p95FrameMs = b.p95FrameMs;
        this.p99FrameMs = b.p99FrameMs;
        this.maxFrameMs = b.maxFrameMs;
        this.spikeCount = b.spikeCount;
        this.frameSampleCount = b.frameSampleCount;
        this.heapUsedBytes = b.heapUsedBytes;
        this.heapCommittedBytes = b.heapCommittedBytes;
        this.heapMaxBytes = b.heapMaxBytes;
        this.gcCountDelta = b.gcCountDelta;
        this.gcTimeMsDelta = b.gcTimeMsDelta;
        this.memoryAvailable = b.memoryAvailable;
        this.entityCount = b.entityCount;
        this.entityAvailable = b.entityAvailable;
        this.blockEntityCount = b.blockEntityCount;
        this.blockEntityAvailable = b.blockEntityAvailable;
        this.loadedChunks = b.loadedChunks;
        this.chunksAvailable = b.chunksAvailable;
        this.chunkEventsPerMin = b.chunkEventsPerMin;
        this.chunkSpikeCoincidences = b.chunkSpikeCoincidences;
        this.pingMs = b.pingMs;
        this.pingJitterMs = b.pingJitterMs;
        this.networkAvailable = b.networkAvailable;
        this.serverTickMs = b.serverTickMs;
        this.serverTickAvailable = b.serverTickAvailable;
        this.serverTickSource = b.serverTickSource;
        this.gcSpikeCoincidences = b.gcSpikeCoincidences;
        this.installedModCount = b.installedModCount;
    }

    /** Empty snapshot: every metric unavailable. Used before the first sample. */
    public static MetricsSnapshot unavailable(long timestampMs) {
        Builder b = new Builder(timestampMs, 0L);
        b.fps = Double.NaN;
        b.avgFrameMs = Double.NaN;
        b.p50FrameMs = Double.NaN;
        b.p95FrameMs = Double.NaN;
        b.p99FrameMs = Double.NaN;
        b.maxFrameMs = Double.NaN;
        b.spikeCount = 0;
        b.frameSampleCount = 0;
        b.heapUsedBytes = -1L;
        b.heapCommittedBytes = -1L;
        b.heapMaxBytes = -1L;
        b.gcCountDelta = -1L;
        b.gcTimeMsDelta = Double.NaN;
        b.memoryAvailable = false;
        b.entityCount = -1;
        b.entityAvailable = false;
        b.blockEntityCount = -1;
        b.blockEntityAvailable = false;
        b.loadedChunks = -1;
        b.chunksAvailable = false;
        b.chunkEventsPerMin = -1;
        b.chunkSpikeCoincidences = 0;
        b.pingMs = Double.NaN;
        b.pingJitterMs = Double.NaN;
        b.networkAvailable = false;
        b.serverTickMs = Double.NaN;
        b.serverTickAvailable = false;
        b.serverTickSource = "unavailable";
        b.gcSpikeCoincidences = 0;
        b.installedModCount = -1;
        return b.build();
    }

    public static final class Builder {
        private final long timestampMs;
        private final long windowMs;
        private double fps = Double.NaN;
        private double avgFrameMs = Double.NaN;
        private double p50FrameMs = Double.NaN;
        private double p95FrameMs = Double.NaN;
        private double p99FrameMs = Double.NaN;
        private double maxFrameMs = Double.NaN;
        private int spikeCount;
        private int frameSampleCount;
        private long heapUsedBytes = -1L;
        private long heapCommittedBytes = -1L;
        private long heapMaxBytes = -1L;
        private long gcCountDelta = -1L;
        private double gcTimeMsDelta = Double.NaN;
        private boolean memoryAvailable;
        private int entityCount = -1;
        private boolean entityAvailable;
        private int blockEntityCount = -1;
        private boolean blockEntityAvailable;
        private int loadedChunks = -1;
        private boolean chunksAvailable;
        private int chunkEventsPerMin = -1;
        private int chunkSpikeCoincidences;
        private double pingMs = Double.NaN;
        private double pingJitterMs = Double.NaN;
        private boolean networkAvailable;
        private double serverTickMs = Double.NaN;
        private boolean serverTickAvailable;
        private String serverTickSource = "unavailable";
        private int gcSpikeCoincidences;
        private int installedModCount = -1;

        public Builder(long timestampMs, long windowMs) {
            this.timestampMs = timestampMs;
            this.windowMs = windowMs;
        }

        public Builder frames(double fps, double avg, double p50, double p95, double p99,
                double max, int spikes, int samples) {
            this.fps = fps;
            this.avgFrameMs = avg;
            this.p50FrameMs = p50;
            this.p95FrameMs = p95;
            this.p99FrameMs = p99;
            this.maxFrameMs = max;
            this.spikeCount = spikes;
            this.frameSampleCount = samples;
            return this;
        }

        public Builder memory(long used, long committed, long max, long gcCount, double gcMs) {
            this.heapUsedBytes = used;
            this.heapCommittedBytes = committed;
            this.heapMaxBytes = max;
            this.gcCountDelta = gcCount;
            this.gcTimeMsDelta = gcMs;
            this.memoryAvailable = true;
            return this;
        }

        public Builder entities(int entities, int blockEntities, boolean beAvailable) {
            this.entityCount = entities;
            this.entityAvailable = true;
            this.blockEntityCount = blockEntities;
            this.blockEntityAvailable = beAvailable;
            return this;
        }

        public Builder chunks(int loaded, int eventsPerMin, int coincidences) {
            this.loadedChunks = loaded;
            this.chunksAvailable = true;
            this.chunkEventsPerMin = eventsPerMin;
            this.chunkSpikeCoincidences = coincidences;
            return this;
        }

        public Builder network(double pingMs, double jitterMs) {
            this.pingMs = pingMs;
            this.pingJitterMs = jitterMs;
            this.networkAvailable = true;
            return this;
        }

        public Builder serverTick(double tickMs, String source) {
            this.serverTickMs = tickMs;
            this.serverTickAvailable = true;
            this.serverTickSource = source;
            return this;
        }

        public Builder gcCoincidences(int coincidences) {
            this.gcSpikeCoincidences = coincidences;
            return this;
        }

        public Builder modCount(int count) {
            this.installedModCount = count;
            return this;
        }

        public MetricsSnapshot build() {
            return new MetricsSnapshot(this);
        }
    }
}
