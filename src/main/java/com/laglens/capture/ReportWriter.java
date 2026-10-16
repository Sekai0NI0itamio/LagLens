package com.laglens.capture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.laglens.analysis.Finding;
import com.laglens.collectors.ModInventoryCollector;
import com.laglens.config.LagLensConfig;
import com.laglens.metrics.FrameStats;
import com.laglens.metrics.MetricsSnapshot;
import com.laglens.util.Format;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Asynchronous local report writer (JSON + Markdown summary).
 *
 * <p>All disk I/O runs on a single background thread; the render thread never
 * blocks on report writes. Filenames are sanitized timestamps, the report
 * directory is dedicated to LagLens, and retention pruning deletes only files
 * matching our own {@code laglens-report-*} prefix. Privacy: when
 * {@code privacyStripEnv} is set, hostname/username are omitted; the player
 * inspects every report before sharing since nothing is ever auto-uploaded.
 */
public final class ReportWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final int MAX_SERIES_POINTS = 600;

    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LagLens-ReportIO");
        t.setDaemon(true);
        return t;
    });

    /** Result of an export: both files (either may be null when disabled/failed). */
    public record ExportResult(Path json, Path markdown, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    public Future<ExportResult> exportAsync(Path gameDir, LagLensConfig config,
            CaptureSession.CaptureResult capture, EnvironmentInfo env,
            List<ModInventoryCollector.ModEntry> mods, List<String> limitations) {
        return io.submit(() -> export(gameDir, config, capture, env, mods, limitations));
    }

    private ExportResult export(Path gameDir, LagLensConfig config,
            CaptureSession.CaptureResult capture, EnvironmentInfo env,
            List<ModInventoryCollector.ModEntry> mods, List<String> limitations) {
        try {
            Path dir = gameDir.resolve(config.reportDir);
            Files.createDirectories(dir);
            String stamp = STAMP.format(Instant.ofEpochMilli(capture.stoppedAtMs()));
            Path json = dir.resolve("laglens-report-" + stamp + ".json");
            Path md = dir.resolve("laglens-report-" + stamp + ".md");
            JsonObject report = buildJson(config, capture, env, mods, limitations);
            try (Writer w = Files.newBufferedWriter(json)) {
                GSON.toJson(report, w);
            }
            Files.writeString(md, buildMarkdown(config, capture, env, mods, limitations));
            prune(dir, config.reportRetention);
            return new ExportResult(json, md, null);
        } catch (IOException | RuntimeException e) {
            return new ExportResult(null, null, e.getMessage());
        }
    }

    /** Synchronous JSON builder, unit-testable without disk access. */
    public JsonObject buildJson(LagLensConfig config, CaptureSession.CaptureResult capture,
            EnvironmentInfo env, List<ModInventoryCollector.ModEntry> mods, List<String> limitations) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", ReportSchema.SCHEMA_VERSION);
        root.addProperty("generatedAt", Instant.ofEpochMilli(capture.stoppedAtMs()).toString());

        JsonObject cap = new JsonObject();
        cap.addProperty("mode", capture.mode().name());
        cap.addProperty("startedAt", Instant.ofEpochMilli(capture.startedAtMs()).toString());
        cap.addProperty("stoppedAt", Instant.ofEpochMilli(capture.stoppedAtMs()).toString());
        cap.addProperty("durationMs", capture.durationMs());
        cap.addProperty("snapshots", capture.snapshots().size());
        cap.addProperty("droppedSnapshots", capture.droppedSnapshots());
        root.add("capture", cap);

        root.add("environment", buildEnvironment(config, env));

        // Frame series (downsampled to a bounded number of points).
        List<MetricsSnapshot> snaps = capture.snapshots();
        JsonArray avgSeries = new JsonArray();
        List<Double> allFrames = new ArrayList<>();
        int step = Math.max(1, snaps.size() / MAX_SERIES_POINTS);
        for (int i = 0; i < snaps.size(); i += step) {
            MetricsSnapshot s = snaps.get(i);
            if (Double.isFinite(s.avgFrameMs)) {
                avgSeries.add(s.avgFrameMs);
                allFrames.add(s.avgFrameMs);
            }
        }
        double[] arr = allFrames.stream().mapToDouble(Double::doubleValue).toArray();
        JsonObject frames = new JsonObject();
        frames.add("avgFrameMsSeries", avgSeries);
        putFinite(frames, "p50", FrameStats.percentile(arr, 50));
        putFinite(frames, "p95", FrameStats.percentile(arr, 95));
        putFinite(frames, "p99", FrameStats.percentile(arr, 99));
        putFinite(frames, "max", FrameStats.max(arr));
        frames.addProperty("units", "milliseconds");
        root.add("frames", frames);

        MetricsSnapshot last = snaps.isEmpty() ? null : snaps.get(snaps.size() - 1);
        root.add("memory", memoryJson(last));
        root.add("entities", entitiesJson(last));
        root.add("chunks", chunksJson(last));
        root.add("network", networkJson(last));
        root.add("server", serverJson(last));

        JsonArray findings = new JsonArray();
        if (capture.findings() != null) {
            for (Finding f : capture.findings()) {
                JsonObject o = new JsonObject();
                o.addProperty("title", f.title());
                o.addProperty("severity", f.severity().name());
                o.addProperty("confidence", f.confidence().name());
                o.addProperty("evidence", f.evidence().name());
                o.addProperty("symptom", f.symptom());
                o.addProperty("page", f.page());
                o.addProperty("nextStep", f.nextStep());
                findings.add(o);
            }
        }
        root.add("findings", findings);

        JsonArray lim = new JsonArray();
        if (limitations != null) {
            limitations.forEach(lim::add);
        }
        root.add("limitations", lim);

        JsonArray modArr = new JsonArray();
        if (mods != null) {
            for (ModInventoryCollector.ModEntry m : mods) {
                JsonObject o = new JsonObject();
                o.addProperty("id", m.id());
                o.addProperty("version", m.version());
                modArr.add(o);
            }
        }
        root.add("mods", modArr);
        return root;
    }

    private JsonObject buildEnvironment(LagLensConfig config, EnvironmentInfo env) {
        JsonObject o = new JsonObject();
        if (env != null) {
            o.addProperty("minecraft", valueOrUnavailable(env.minecraftVersion()));
            o.addProperty("loader", valueOrUnavailable(env.loaderVersion()));
            o.addProperty("laglens", valueOrUnavailable(env.laglensVersion()));
            o.addProperty("java", valueOrUnavailable(env.javaVersion()));
            if (config.includeEnvMetadata) {
                o.addProperty("os", valueOrUnavailable(env.os()));
                o.addProperty("cpu", valueOrUnavailable(env.cpu()));
                o.addProperty("gpu", valueOrUnavailable(env.gpu()));
                o.addProperty("maxHeap", valueOrUnavailable(env.maxHeap()));
                if (!config.privacyStripEnv) {
                    o.addProperty("user", valueOrUnavailable(env.user()));
                    o.addProperty("host", valueOrUnavailable(env.host()));
                }
            }
        }
        return o;
    }

    private static String valueOrUnavailable(String v) {
        return v == null || v.isBlank() ? "unavailable" : v;
    }

    private static void putFinite(JsonObject o, String key, double v) {
        if (Double.isFinite(v)) {
            o.addProperty(key, v);
        } else {
            o.addProperty(key, "unavailable");
        }
    }

    private static JsonObject memoryJson(MetricsSnapshot s) {
        JsonObject o = new JsonObject();
        if (s != null && s.memoryAvailable) {
            o.addProperty("heapUsedBytes", s.heapUsedBytes);
            o.addProperty("heapCommittedBytes", s.heapCommittedBytes);
            o.addProperty("heapMaxBytes", s.heapMaxBytes);
            o.addProperty("gcCountDelta", s.gcCountDelta);
            putFinite(o, "gcTimeMsDelta", s.gcTimeMsDelta);
        } else {
            o.addProperty("status", "unavailable");
        }
        return o;
    }

    private static JsonObject entitiesJson(MetricsSnapshot s) {
        JsonObject o = new JsonObject();
        if (s != null && s.entityAvailable) {
            o.addProperty("total", s.entityCount);
            if (s.blockEntityAvailable) {
                o.addProperty("blockEntities", s.blockEntityCount);
            } else {
                o.addProperty("blockEntities", "unavailable");
            }
        } else {
            o.addProperty("status", "unavailable");
        }
        return o;
    }

    private static JsonObject chunksJson(MetricsSnapshot s) {
        JsonObject o = new JsonObject();
        if (s != null && s.chunksAvailable) {
            o.addProperty("loaded", s.loadedChunks);
            o.addProperty("eventsPerMin", s.chunkEventsPerMin);
            o.addProperty("spikeCoincidences", s.chunkSpikeCoincidences);
        } else {
            o.addProperty("status", "unavailable");
        }
        return o;
    }

    private static JsonObject networkJson(MetricsSnapshot s) {
        JsonObject o = new JsonObject();
        if (s != null && s.networkAvailable) {
            putFinite(o, "pingMs", s.pingMs);
            putFinite(o, "jitterMs", s.pingJitterMs);
        } else {
            o.addProperty("status", "unavailable");
        }
        return o;
    }

    private static JsonObject serverJson(MetricsSnapshot s) {
        JsonObject o = new JsonObject();
        if (s != null && s.serverTickAvailable) {
            putFinite(o, "tickMs", s.serverTickMs);
            o.addProperty("source", s.serverTickSource);
        } else {
            o.addProperty("status", "unavailable (no integrated server or companion)");
        }
        return o;
    }

    private String buildMarkdown(LagLensConfig config, CaptureSession.CaptureResult capture,
            EnvironmentInfo env, List<ModInventoryCollector.ModEntry> mods, List<String> limitations) {
        StringBuilder md = new StringBuilder();
        md.append("# LagLens report (").append(ReportSchema.SCHEMA_VERSION).append(")\n\n");
        md.append("- Mode: ").append(capture.mode()).append('\n');
        md.append("- Duration: ").append(Format.duration(capture.durationMs())).append('\n');
        md.append("- Snapshots: ").append(capture.snapshots().size());
        if (capture.droppedSnapshots() > 0) {
            md.append(" (").append(capture.droppedSnapshots()).append(" dropped at cap)");
        }
        md.append('\n');
        if (env != null && env.minecraftVersion() != null) {
            md.append("- Minecraft ").append(env.minecraftVersion());
            if (env.laglensVersion() != null) {
                md.append(", LagLens ").append(env.laglensVersion());
            }
            md.append('\n');
        }
        md.append("\n## Findings\n");
        if (capture.findings() == null || capture.findings().isEmpty()) {
            md.append("No findings (capture ended before analysis ran).\n");
        } else {
            for (Finding f : capture.findings()) {
                md.append("\n### ").append(f.title()).append('\n');
                md.append("- Severity: ").append(f.severity().label())
                        .append(", confidence: ").append(f.confidence().label())
                        .append(", evidence: ").append(f.evidence().label()).append('\n');
                md.append("- Symptom: ").append(f.symptom()).append('\n');
                md.append("- Next step: ").append(f.nextStep()).append('\n');
            }
        }
        md.append("\n## Limitations\n");
        if (limitations == null || limitations.isEmpty()) {
            md.append("None recorded.\n");
        } else {
            limitations.forEach(l -> md.append("- ").append(l).append('\n'));
        }
        md.append("\nFull machine-readable data is in the accompanying JSON file.\n");
        return md.toString();
    }

    private void prune(Path dir, int retention) throws IOException {
        List<Path> reports = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "laglens-report-*.json")) {
            stream.forEach(reports::add);
        }
        reports.sort(Comparator.comparing(p -> p.getFileName().toString()));
        while (reports.size() > Math.max(1, retention)) {
            Path oldest = reports.remove(0);
            Files.deleteIfExists(oldest);
            Path md = dir.resolve(oldest.getFileName().toString().replace(".json", ".md"));
            Files.deleteIfExists(md);
        }
    }

    public void shutdown() {
        io.shutdown();
    }

    /** Environment facts collected once per export (all fields optional). */
    public record EnvironmentInfo(String minecraftVersion, String loaderVersion, String laglensVersion,
            String javaVersion, String os, String cpu, String gpu, String maxHeap, String user,
            String host) {
    }
}
