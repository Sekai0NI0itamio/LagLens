package com.laglens.session;

import com.laglens.analysis.DiagnosticEngine;
import com.laglens.analysis.Finding;
import com.laglens.capture.CaptureSession;
import com.laglens.collectors.ChunkCollector;
import com.laglens.collectors.EntityCollector;
import com.laglens.collectors.FrameTimeCollector;
import com.laglens.collectors.MemoryCollector;
import com.laglens.collectors.ModInventoryCollector;
import com.laglens.collectors.NetworkCollector;
import com.laglens.collectors.ServerTickCollector;
import com.laglens.config.LagLensConfig;
import com.laglens.metrics.Clock;
import com.laglens.metrics.MetricsSnapshot;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Owns every collector, runs the diagnostic engine, and orchestrates captures.
 *
 * <p>Threading: all methods are called on the client thread (tick callbacks
 * and the HUD-render callback both run there). Collectors synchronize
 * internally so the UI (same thread) and the report thread (read-only,
 * immutable snapshots) cannot race.
 *
 * <p>Overhead: frame hook does one nanotime + ring write; 1 Hz samplers do
 * bounded iteration. Self-measured cost is exposed for the Settings page so
 * LagLens can be held to "never become a major source of lag".
 */
public final class LagLensSession {
    /** Ticks between 1 Hz sampler runs at 20 TPS. */
    private static final int SAMPLE_TICKS = 20;
    /** Coincidence radius between a spike and a chunk event. */
    private static final long CHUNK_COINCIDENCE_MS = 500L;
    /** How long after a GC pause a spike still counts as overlapping it. */
    private static final long GC_COINCIDENCE_MS = 2000L;

    private final LagLensConfig config;
    private final FrameTimeCollector frames;
    private final MemoryCollector memory;
    private final EntityCollector entities;
    private final ChunkCollector chunks;
    private final NetworkCollector network;
    private final ServerTickCollector serverTick;
    private final ModInventoryCollector modInventory;
    private final CaptureSession capture;

    private final Deque<Long> spikeTimes = new ArrayDeque<>();
    private final Deque<GcMark> gcHistory = new ArrayDeque<>();
    private List<Finding> findings = List.of();
    private MetricsSnapshot lastSnapshot;
    /** A capture that hit its auto-stop deadline; picked up by the mod entrypoint. */
    private volatile CaptureSession.CaptureResult autoStoppedResult;

    private long tickCounter;
    private long overheadNanos;
    private long overheadSamples;
    private MinecraftWorldReader worldReader = new NullWorldReader();

    /** Seam for world access: real implementation lives in client code, tests use the null reader. */
    public interface MinecraftWorldReader {
        void sample(LagLensSession session, MinecraftClient client);
    }

    private record GcMark(long atMs, long count, long timeMs) {
    }

    public LagLensSession(LagLensConfig config) {
        this.config = config;
        this.frames = new FrameTimeCollector(config);
        this.memory = new MemoryCollector();
        this.entities = new EntityCollector();
        this.chunks = new ChunkCollector();
        this.network = new NetworkCollector();
        this.serverTick = new ServerTickCollector();
        this.modInventory = new ModInventoryCollector();
        this.capture = new CaptureSession();
        this.lastSnapshot = MetricsSnapshot.unavailable(Clock.milliTime());
    }

    public void setWorldReader(MinecraftWorldReader reader) {
        this.worldReader = reader == null ? new NullWorldReader() : reader;
    }

    /** Per-frame hook (HUD-render event, render thread = client thread). */
    public void onFrame() {
        long t0 = Clock.nanoTime();
        int before;
        synchronized (frames) {
            before = frames.sampleCount();
        }
        frames.recordFrame();
        double delta;
        synchronized (frames) {
            delta = frames.lastDeltaMs();
        }
        if (Double.isFinite(delta) && delta > config.spikeWarnMs && framesSampleDeltaIsNew(before)) {
            long nowMs = Clock.milliTime();
            spikeTimes.addLast(nowMs);
            while (spikeTimes.size() > 3600) {
                spikeTimes.removeFirst();
            }
            if (delta > config.spikeSevereMs && config.autoCaptureOnSevereSpike
                    && capture.state() == CaptureSession.State.IDLE) {
                capture.start(CaptureSession.Mode.LIGHT, nowMs, config.autoCaptureSec * 1000L);
            }
        }
        addOverhead(Clock.nanoTime() - t0);
    }

    private boolean framesSampleDeltaIsNew(int before) {
        synchronized (frames) {
            return frames.sampleCount() != before;
        }
    }

    /** End-of-tick hook: 1 Hz sampling, engine, capture bookkeeping. */
    public void onClientTick(MinecraftClient client) {
        long t0 = Clock.nanoTime();
        tickCounter++;
        frames.setCurrentFps(client.getCurrentFps());
        if (tickCounter % SAMPLE_TICKS == 0) {
            sampleOnce(client);
        }
        if (capture.state() == CaptureSession.State.RECORDING) {
            if (capture.checkAutoStop(Clock.milliTime())) {
                // Only spike-triggered auto-captures carry a deadline (manual
                // captures pass 0 and never land here). Never discard silently:
                // the entrypoint announces this in chat and exports it.
                autoStoppedResult = capture.stop(findings);
            }
        }
        addOverhead(Clock.nanoTime() - t0);
    }

    private void sampleOnce(MinecraftClient client) {
        long nowMs = Clock.milliTime();
        memory.sample(nowMs);
        recordGcMark(nowMs);
        try {
            worldReader.sample(this, client);
        } catch (Exception e) {
            entities.markUnavailable();
            chunks.markUnavailable();
            network.reset();
            serverTick.markUnavailable();
        }
        lastSnapshot = buildSnapshot(nowMs);
        findings = DiagnosticEngine.analyze(lastSnapshot, config);
        if (capture.state() == CaptureSession.State.RECORDING) {
            capture.record(lastSnapshot);
        }
    }

    private void recordGcMark(long nowMs) {
        if (!memory.available()) {
            return;
        }
        gcHistory.addLast(new GcMark(nowMs, memory.totalGcCount(), memory.totalGcTimeMs()));
        while (gcHistory.size() > 180) {
            gcHistory.removeFirst();
        }
    }

    /** Builds the typed snapshot the engine, UI and reports share. */
    public MetricsSnapshot buildSnapshot(long nowMs) {
        long windowMs = config.historySeconds * 1000L;
        MetricsSnapshot.Builder b = new MetricsSnapshot.Builder(nowMs, windowMs);

        double[] samples;
        int count;
        synchronized (frames) {
            samples = frames.samples();
            count = frames.sampleCount();
        }
        if (count > 0) {
            double p50 = percentileOf(samples, 50);
            double p95 = percentileOf(samples, 95);
            double p99 = percentileOf(samples, 99);
            double avg = averageOf(samples);
            double max = maxOf(samples);
            int spikes = countAbove(samples, config.spikeWarnMs);
            double fps = frames.currentFps() >= 0 ? frames.currentFps()
                    : com.laglens.metrics.FrameStats.frameMsToFps(avg);
            b.frames(fps, avg, p50, p95, p99, max, spikes, count);
        } else {
            b.frames(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, 0);
        }

        if (memory.available()) {
            long[] gcDelta = gcDeltaOver(60_000L, nowMs);
            b.memory(memory.heapUsedBytes(), memory.heapCommittedBytes(), memory.heapMaxBytes(),
                    gcDelta[0], gcDelta[1]);
        }
        EntityCollector.Census census = entities.census();
        if (census.available()) {
            EntityCollector.BlockEntities be = entities.blockEntities();
            b.entities(census.total(), be.total(), be.available());
        }
        if (chunks.available()) {
            int coincidences = countChunkCoincidences(nowMs, windowMs);
            b.chunks(chunks.loadedChunks(), chunks.eventsPerMinute(), coincidences);
        }
        if (network.available()) {
            b.network(network.currentPingMs(), network.jitterMs());
        }
        if (serverTick.available()) {
            b.serverTick(serverTick.tickMs(), serverTick.source());
        }
        b.gcCoincidences(countGcCoincidences(nowMs, windowMs));
        b.modCount(modInventory.modCount());
        return b.build();
    }

    private int countChunkCoincidences(long nowMs, long windowMs) {
        int count = 0;
        for (long spike : spikeTimes) {
            if (nowMs - spike > windowMs) {
                continue;
            }
            if (chunks.eventsNear(spike, CHUNK_COINCIDENCE_MS) > 0) {
                count++;
            }
        }
        return count;
    }

    private int countGcCoincidences(long nowMs, long windowMs) {
        long lastPauseAt = memory.lastGcPauseAtMs();
        if (lastPauseAt < 0) {
            // Fall back to GC-history marks: any collection inside the window counts
            // only when a spike followed it closely; without pause timestamps, be strict.
            return 0;
        }
        int count = 0;
        for (long spike : spikeTimes) {
            if (nowMs - spike > windowMs) {
                continue;
            }
            if (spike >= lastPauseAt && spike - lastPauseAt <= GC_COINCIDENCE_MS) {
                count++;
            }
        }
        return count;
    }

    private long[] gcDeltaOver(long windowMs, long nowMs) {
        long count = 0L;
        long time = 0L;
        GcMark baseline = null;
        for (GcMark m : gcHistory) {
            if (nowMs - m.atMs() <= windowMs && baseline == null) {
                baseline = m;
            }
        }
        GcMark latest = gcHistory.peekLast();
        if (baseline != null && latest != null && latest.count() >= baseline.count()
                && latest.timeMs() >= baseline.timeMs()) {
            count = latest.count() - baseline.count();
            time = latest.timeMs() - baseline.timeMs();
        }
        return new long[]{count, time};
    }

    private static double averageOf(double[] s) {
        return com.laglens.metrics.FrameStats.average(s);
    }

    private static double percentileOf(double[] s, double p) {
        return com.laglens.metrics.FrameStats.percentile(s, p);
    }

    private static double maxOf(double[] s) {
        return com.laglens.metrics.FrameStats.max(s);
    }

    private static int countAbove(double[] s, double t) {
        return com.laglens.metrics.SpikeDetector.countAbove(s, t);
    }

    private void addOverhead(long nanos) {
        overheadNanos += nanos;
        overheadSamples++;
    }

    /** Mean LagLens hook cost in microseconds (frame + tick paths combined). */
    public synchronized double meanOverheadMicros() {
        if (overheadSamples == 0) {
            return Double.NaN;
        }
        return (overheadNanos / (double) overheadSamples) / 1000.0;
    }

    /** Short status lines for `/laglens status` chat output. */
    public List<String> statusLines() {
        MetricsSnapshot s = lastSnapshot;
        List<String> lines = new ArrayList<>();
        lines.add(String.format("FPS %.0f | P95 %s | spikes %d (%d samples)",
                s.fps, fmt(s.p95FrameMs), s.spikeCount, s.frameSampleCount));
        lines.add(s.memoryAvailable
                ? "Heap " + com.laglens.util.Format.bytes(s.heapUsedBytes) + " / "
                        + com.laglens.util.Format.bytes(s.heapMaxBytes)
                : "Heap: unavailable");
        lines.add(s.entityAvailable ? "Entities: " + s.entityCount : "Entities: unavailable");
        lines.add(s.networkAvailable ? "Ping: " + Math.round(s.pingMs) + " ms"
                : "Ping: unavailable");
        lines.add("Findings: " + findings.size()
                + (capture.state() == CaptureSession.State.RECORDING ? " | RECORDING" : ""));
        return lines;
    }

    /** Honest limitation strings embedded in reports and shown in the UI. */
    public List<String> limitations() {
        List<String> out = new ArrayList<>();
        out.add("Frame times measure the render-loop period at the HUD pass with a monotonic clock; "
                + "not GPU execution time.");
        out.add("Chunk mesh-rebuild and upload timings are not exposed by the vanilla pipeline "
                + "and are reported as unavailable.");
        if (!network.packetInspectionAvailable()) {
            out.add("Packet inspection is not instrumented; network view covers latency and "
                    + "connectivity only.");
        }
        if (!serverTick.available()) {
            out.add("No authoritative server tick data (no integrated server or companion); "
                    + "server performance shown as unavailable, never guessed from ping.");
        }
        out.add("Per-mod CPU attribution requires a sampling profiler integration; presence in "
                + "the mod list is not evidence of cost.");
        return out;
    }

    public LagLensConfig config() {
        return config;
    }

    public FrameTimeCollector frames() {
        return frames;
    }

    public MemoryCollector memory() {
        return memory;
    }

    public EntityCollector entities() {
        return entities;
    }

    public ChunkCollector chunks() {
        return chunks;
    }

    public NetworkCollector network() {
        return network;
    }

    public ServerTickCollector serverTick() {
        return serverTick;
    }

    public ModInventoryCollector modInventory() {
        return modInventory;
    }

    public CaptureSession capture() {
        return capture;
    }

    public List<Finding> findings() {
        return findings;
    }

    /** Returns and clears a capture that hit its auto-stop deadline, if any. */
    public synchronized CaptureSession.CaptureResult takeAutoStopped() {
        CaptureSession.CaptureResult result = autoStoppedResult;
        autoStoppedResult = null;
        return result;
    }

    public MetricsSnapshot lastSnapshot() {
        return lastSnapshot;
    }

    private static String fmt(double v) {
        return com.laglens.util.Format.ms(v);
    }

    private static final class NullWorldReader implements MinecraftWorldReader {
        @Override
        public void sample(LagLensSession session, MinecraftClient client) {
            // No world access (used by unit tests and before init completes).
        }
    }

    /** Publishes an entity census gathered by the client-side world reader. */
    public void publishEntityCensus(long nowMs, int total, int players, Map<String, Integer> byType,
            boolean detailed) {
        entities.publish(nowMs, total, players, detailed ? byType : null);
    }

    /** Publishes block-entity totals gathered by bounded chunk sampling. */
    public void publishBlockEntities(long nowMs, int total, Map<String, Integer> byType,
            boolean detailed) {
        entities.publishBlockEntities(nowMs, total, byType, detailed);
    }
}
