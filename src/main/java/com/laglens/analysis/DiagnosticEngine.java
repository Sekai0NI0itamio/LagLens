package com.laglens.analysis;

import com.laglens.config.LagLensConfig;
import com.laglens.metrics.MetricsSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rules-based diagnostic engine (pure logic, no Minecraft classes).
 *
 * <p>Cases implemented:
 * <ul>
 *   <li>A: chunk-related stutter (spikes coincide with chunk load/rebuild events).</li>
 *   <li>B: GC-related stutter (long GC pauses overlap frame spikes).</li>
 *   <li>C: network latency with stable frame times (responsiveness, not rendering).</li>
 *   <li>D: rendering workload (high frame percentiles, no GC/chunk coincidence).</li>
 *   <li>E: server overload (only with authoritative tick data: integrated server or companion).</li>
 *   <li>F: insufficient evidence (spikes without an attributable cause).</li>
 * </ul>
 * Rules account for sample size (minimum frame samples required), missing
 * data (unavailable metrics disable their rules instead of voting zero), and
 * coincidence counts before promoting a correlation above
 * {@link Evidence#POSSIBLE_CONTRIBUTOR}.
 */
public final class DiagnosticEngine {

    /** Minimum frame samples before any spike-based rule may fire. */
    public static final int MIN_FRAME_SAMPLES = 30;
    /** Coincidences needed to call a correlation strong. */
    public static final int STRONG_COINCIDENCE_MIN = 3;

    private DiagnosticEngine() {
    }

    public static List<Finding> analyze(MetricsSnapshot s, LagLensConfig cfg) {
        List<Finding> findings = new ArrayList<>();
        if (s.frameSampleCount < MIN_FRAME_SAMPLES) {
            findings.add(new Finding.Builder("Collecting baseline measurements")
                    .page("overview")
                    .severity(Severity.INFO)
                    .confidence(Confidence.LOW)
                    .evidence(Evidence.INSUFFICIENT_EVIDENCE)
                    .symptom("Only " + s.frameSampleCount + " frame samples in the current window.")
                    .addEvidence("Need at least " + MIN_FRAME_SAMPLES + " samples before spike rules apply.")
                    .windowMs(s.windowMs)
                    .unknowns("Whether the current smoothness is representative.")
                    .nextStep("Play normally for a minute, then re-check Bottlenecks.")
                    .build());
            return findings;
        }

        boolean spiking = s.spikeCount > 0;
        boolean framesBad = Double.isFinite(s.p95FrameMs) && s.p95FrameMs > cfg.spikeWarnMs;

        // Case E: server overload, authoritative data only.
        if (s.serverTickAvailable && Double.isFinite(s.serverTickMs)
                && s.serverTickMs > cfg.serverTickWarnMs) {
            findings.add(new Finding.Builder("Server tick overload ("
                    + ("integrated".equals(s.serverTickSource) ? "integrated server" : "companion data") + ")")
                    .page("server")
                    .severity(s.serverTickMs > 100.0 ? Severity.CRITICAL : Severity.WARN)
                    .confidence(Confidence.HIGH)
                    .evidence(Evidence.DIRECT)
                    .symptom("Average server tick " + fmtMs(s.serverTickMs)
                            + " exceeds the 50 ms budget for 20 TPS.")
                    .addEvidence("Server tick avg " + fmtMs(s.serverTickMs)
                            + " over " + (s.windowMs / 1000) + " s (" + s.serverTickSource + ").")
                    .windowMs(s.windowMs)
                    .unknowns("Which server-side subsystem is slow without a server profiler.")
                    .nextStep("For singleplayer, reduce simulation distance; for servers, ask an admin for a Spark profile.")
                    .build());
        }

        // Case B: GC-related stutter.
        if (s.memoryAvailable && spiking && s.gcSpikeCoincidences >= 1
                && Double.isFinite(s.gcTimeMsDelta) && s.gcTimeMsDelta > cfg.gcTimeWarnMs) {
            Evidence ev = s.gcSpikeCoincidences >= STRONG_COINCIDENCE_MIN
                    ? Evidence.STRONG_CORRELATION : Evidence.POSSIBLE_CONTRIBUTOR;
            findings.add(new Finding.Builder("GC pauses coincide with frame spikes")
                    .page("memory")
                    .severity(Severity.WARN)
                    .confidence(s.gcSpikeCoincidences >= STRONG_COINCIDENCE_MIN
                            ? Confidence.MEDIUM : Confidence.LOW)
                    .evidence(ev)
                    .symptom(s.spikeCount + " frame spikes with " + fmtMs(s.gcTimeMsDelta)
                            + " in GC pauses over the window.")
                    .addEvidence(s.gcSpikeCoincidences + " GC events overlapped a frame spike.")
                    .addEvidence("GC time in window: " + fmtMs(s.gcTimeMsDelta)
                            + " across " + s.gcCountDelta + " collections.")
                    .windowMs(s.windowMs)
                    .unknowns("Whether allocation comes from terrain, entities, or a specific mod.")
                    .nextStep("Run a 60 s detailed capture while the stutter reproduces, then compare heap trend post-GC.")
                    .build());
        }

        // Case A: chunk-related stutter.
        if (s.chunksAvailable && spiking && s.chunkEventsPerMin >= cfg.chunkEventsWarnPerMin
                && s.chunkSpikeCoincidences >= 1) {
            Evidence ev = s.chunkSpikeCoincidences >= STRONG_COINCIDENCE_MIN
                    ? Evidence.STRONG_CORRELATION : Evidence.POSSIBLE_CONTRIBUTOR;
            findings.add(new Finding.Builder("Frame spikes coincide with chunk activity")
                    .page("terrain")
                    .severity(Severity.WARN)
                    .confidence(s.chunkSpikeCoincidences >= STRONG_COINCIDENCE_MIN
                            ? Confidence.MEDIUM : Confidence.LOW)
                    .evidence(ev)
                    .symptom(s.chunkSpikeCoincidences + " of " + s.spikeCount
                            + " spikes occurred within 500 ms of a chunk load/unload event.")
                    .addEvidence("Chunk events: ~" + s.chunkEventsPerMin + "/min; loaded chunks: "
                            + (s.loadedChunks >= 0 ? s.loadedChunks : "unknown") + ".")
                    .addEvidence("Receiving a chunk packet is not proof the client generated terrain; "
                            + "see Terrain page for the measured stage.")
                    .windowMs(s.windowMs)
                    .unknowns("Whether cost is mesh rebuild, upload, allocation, or server delivery delay.")
                    .nextStep("Fly or run in one direction to reproduce, then start a detailed capture.")
                    .build());
        }

        // Case C: network latency with stable frames.
        if (s.networkAvailable && Double.isFinite(s.pingMs) && s.pingMs > cfg.pingWarnMs && !framesBad) {
            findings.add(new Finding.Builder("High latency, but client rendering is stable")
                    .page("network")
                    .severity(Severity.WARN)
                    .confidence(Confidence.MEDIUM)
                    .evidence(Evidence.DIRECT)
                    .symptom("Ping " + Math.round(s.pingMs) + " ms while P95 frame time "
                            + fmtMs(s.p95FrameMs) + " stays smooth.")
                    .addEvidence("Ping " + Math.round(s.pingMs) + " ms"
                            + (Double.isFinite(s.pingJitterMs) ? " (jitter " + Math.round(s.pingJitterMs) + " ms)" : "")
                            + "; P95 frame " + fmtMs(s.p95FrameMs) + ".")
                    .addEvidence("Stable frames with high ping point at responsiveness, not FPS.")
                    .windowMs(s.windowMs)
                    .unknowns("Packet loss cannot be measured from application-level observations.")
                    .nextStep("Check the Network page history; if jitter is high, test with a wired connection.")
                    .build());
        }

        // Case D: rendering workload.
        if (framesBad && s.gcSpikeCoincidences == 0 && s.chunkSpikeCoincidences == 0) {
            String extra = "";
            if (s.entityAvailable && s.entityCount >= cfg.entityWarnCount) {
                extra = " Loaded entities: " + s.entityCount + ".";
            }
            findings.add(new Finding.Builder("Rendering workload is the likely bottleneck")
                    .page("rendering")
                    .severity(s.p95FrameMs > cfg.spikeSevereMs ? Severity.CRITICAL : Severity.WARN)
                    .confidence(Confidence.MEDIUM)
                    .evidence(Evidence.STRONG_CORRELATION)
                    .symptom("P95 frame time " + fmtMs(s.p95FrameMs)
                            + " with no GC or chunk coincidence." + extra)
                    .addEvidence("P50 " + fmtMs(s.p50FrameMs) + ", P95 " + fmtMs(s.p95FrameMs)
                            + ", P99 " + fmtMs(s.p99FrameMs) + ", max " + fmtMs(s.maxFrameMs) + ".")
                    .addEvidence("No GC pause or chunk event overlapped the spikes in this window.")
                    .windowMs(s.windowMs)
                    .unknowns("CPU vs GPU bound cannot be separated without GPU timings (optional integration).")
                    .nextStep("Lower render distance one step and compare P95; see Rendering page.")
                    .build());
        }

        // Case F: spikes without attribution.
        if (spiking && findings.isEmpty()) {
            findings.add(new Finding.Builder("Spikes detected, cause not yet isolated")
                    .page("capture")
                    .severity(Severity.WARN)
                    .confidence(Confidence.LOW)
                    .evidence(Evidence.INSUFFICIENT_EVIDENCE)
                    .symptom(s.spikeCount + " spikes in the window, but no category shows a strong coincidence.")
                    .addEvidence("Spikes: " + s.spikeCount + "; GC coincidences: " + s.gcSpikeCoincidences
                            + "; chunk coincidences: " + s.chunkSpikeCoincidences + ".")
                    .windowMs(s.windowMs)
                    .unknowns("Which subsystem produced the slow frames.")
                    .nextStep("Start a detailed capture, reproduce the stutter for 30-60 s, then export the report.")
                    .build());
        }

        if (!spiking && findings.isEmpty()) {
            findings.add(new Finding.Builder("No significant spikes in this window")
                    .page("overview")
                    .severity(Severity.INFO)
                    .confidence(Confidence.MEDIUM)
                    .evidence(Evidence.DIRECT)
                    .symptom("Frame times are within thresholds (P95 " + fmtMs(s.p95FrameMs) + ").")
                    .addEvidence("Spikes above " + cfg.spikeWarnMs + " ms: 0 over " + (s.windowMs / 1000) + " s.")
                    .windowMs(s.windowMs)
                    .unknowns("Nothing right now; transient issues may need a longer window.")
                    .nextStep("If lag is intermittent, enable automatic capture on severe spikes in Settings.")
                    .build());
        }

        findings.sort(Comparator.comparingInt((Finding f) -> f.severity().ordinal()).reversed());
        return findings;
    }

    private static String fmtMs(double ms) {
        if (!Double.isFinite(ms)) {
            return "unavailable";
        }
        return String.format("%.1f ms", ms);
    }
}
