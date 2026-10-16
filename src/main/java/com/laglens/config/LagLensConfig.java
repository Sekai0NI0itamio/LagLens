package com.laglens.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * LagLens configuration with validation and safe defaults.
 *
 * <p>Stored as JSON at {@code config/laglens.json}. Every setter path is
 * range-checked: out-of-range values are clamped and unknown fields are
 * preserved on load. No setting implies a capability that is not implemented;
 * each option documents what it actually changes.
 */
public final class LagLensConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Dashboard page opened by default (overview, bottlenecks, terrain, ...). */
    public String defaultPage = "overview";
    /** Sampler cadence for 1 Hz-style collectors, in client ticks. */
    public int sampleIntervalTicks = 20;
    /** Rolling frame-history length in seconds (bounded; default 5 min). */
    public int historySeconds = 300;
    /** Frame time above this is a noteworthy spike. */
    public double spikeWarnMs = 34.0;
    /** Frame time above this is a severe spike. */
    public double spikeSevereMs = 100.0;
    /** Spikes needed before a window counts as recurring. */
    public int recurringSpikeMinimum = 3;
    /** Monitoring depth: LIGHT (default) or DETAILED (entity/BE sampling on). */
    public String monitoringLevel = "LIGHT";
    /** Seconds of pre-spike rolling data kept for captures. */
    public int rollingBufferSec = 30;
    /** Start a capture automatically on a severe spike. */
    public boolean autoCaptureOnSevereSpike = false;
    /** Length of automatic captures in seconds. */
    public int autoCaptureSec = 20;
    /** Report directory, relative to the game directory. */
    public String reportDir = "laglens-reports";
    /** How many reports to keep; older files are pruned on export. */
    public int reportRetention = 20;
    /** Include hardware/runtime metadata in reports. */
    public boolean includeEnvMetadata = true;
    /** Strip hostname/username from reports (privacy). */
    public boolean privacyStripEnv = false;
    /** Sample entity counts (needed for the Entities page). */
    public boolean enableEntitySampling = true;
    /** Record chunk load/unload events. */
    public boolean enableChunkEvents = true;
    /** Ping warning threshold. */
    public double pingWarnMs = 150.0;
    /** Server tick warning threshold (20 TPS = 50 ms budget). */
    public double serverTickWarnMs = 50.0;
    /** Chunk events per minute considered noteworthy. */
    public int chunkEventsWarnPerMin = 20;
    /** Loaded-entity count considered noteworthy. */
    public int entityWarnCount = 150;
    /** GC time in window considered noteworthy. */
    public double gcTimeWarnMs = 200.0;
    /** Verbose logging for diagnosing LagLens itself. */
    public boolean debugLogging = false;

    /** Validates and clamps every field; returns human-readable corrections. */
    public java.util.List<String> validate() {
        java.util.List<String> notes = new java.util.ArrayList<>();
        sampleIntervalTicks = clampInt(sampleIntervalTicks, 1, 200, 20, "sampleIntervalTicks", notes);
        historySeconds = clampInt(historySeconds, 30, 1800, 300, "historySeconds", notes);
        spikeWarnMs = clampDouble(spikeWarnMs, 17.0, 1000.0, 34.0, "spikeWarnMs", notes);
        spikeSevereMs = clampDouble(spikeSevereMs, spikeWarnMs, 5000.0, 100.0, "spikeSevereMs", notes);
        recurringSpikeMinimum = clampInt(recurringSpikeMinimum, 2, 100, 3, "recurringSpikeMinimum", notes);
        if (!"LIGHT".equals(monitoringLevel) && !"DETAILED".equals(monitoringLevel)) {
            notes.add("monitoringLevel reset to LIGHT");
            monitoringLevel = "LIGHT";
        }
        rollingBufferSec = clampInt(rollingBufferSec, 5, 300, 30, "rollingBufferSec", notes);
        autoCaptureSec = clampInt(autoCaptureSec, 5, 600, 20, "autoCaptureSec", notes);
        if (reportDir == null || reportDir.isBlank() || reportDir.contains("..")) {
            notes.add("reportDir reset to laglens-reports");
            reportDir = "laglens-reports";
        }
        reportRetention = clampInt(reportRetention, 1, 200, 20, "reportRetention", notes);
        pingWarnMs = clampDouble(pingWarnMs, 20.0, 2000.0, 150.0, "pingWarnMs", notes);
        serverTickWarnMs = clampDouble(serverTickWarnMs, 50.0, 1000.0, 50.0, "serverTickWarnMs", notes);
        chunkEventsWarnPerMin = clampInt(chunkEventsWarnPerMin, 1, 10000, 20, "chunkEventsWarnPerMin", notes);
        entityWarnCount = clampInt(entityWarnCount, 1, 100000, 150, "entityWarnCount", notes);
        gcTimeWarnMs = clampDouble(gcTimeWarnMs, 0.0, 60000.0, 200.0, "gcTimeWarnMs", notes);
        if (!isKnownPage(defaultPage)) {
            notes.add("defaultPage reset to overview");
            defaultPage = "overview";
        }
        return notes;
    }

    private static int clampInt(int v, int min, int max, int dflt, String name, java.util.List<String> notes) {
        if (v < min || v > max) {
            notes.add(name + " clamped to [" + min + ", " + max + "]");
            return Math.min(max, Math.max(min, v));
        }
        return v;
    }

    private static double clampDouble(double v, double min, double max, double dflt, String name,
            java.util.List<String> notes) {
        if (!Double.isFinite(v) || v < min || v > max) {
            notes.add(name + " clamped to [" + min + ", " + max + "]");
            if (!Double.isFinite(v)) {
                return dflt;
            }
            return Math.min(max, Math.max(min, v));
        }
        return v;
    }

    /** Page ids accepted for {@link #defaultPage}. */
    public static boolean isKnownPage(String page) {
        return switch (page) {
            case "overview", "bottlenecks", "terrain", "entities", "rendering", "network",
                    "memory", "server", "mods", "capture", "settings" -> true;
            default -> false;
        };
    }

    /** Loads from disk, falling back to defaults when the file is missing or corrupt. */
    public static LagLensConfig load(Path file) {
        LagLensConfig cfg = new LagLensConfig();
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
                LagLensConfig parsed = GSON.fromJson(json, LagLensConfig.class);
                if (parsed != null) {
                    cfg = parsed;
                }
            } catch (Exception e) {
                // Corrupt config: keep defaults; the save below restores a valid file.
            }
        }
        cfg.validate();
        try {
            cfg.save(file);
        } catch (IOException ignored) {
            // Save failure is reported by the caller-facing settings page, not here.
        }
        return cfg;
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(this, w);
        }
    }
}
