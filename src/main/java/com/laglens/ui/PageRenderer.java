package com.laglens.ui;

import com.laglens.analysis.Finding;
import com.laglens.capture.CaptureSession;
import com.laglens.client.ClientWorldReader;
import com.laglens.collectors.EntityCollector;
import com.laglens.collectors.ModInventoryCollector;
import com.laglens.metrics.MetricsSnapshot;
import com.laglens.session.LagLensSession;
import com.laglens.util.Format;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Draws every dashboard page. Each page states its measurement window and
 * units, shows real values where available, and explains precisely what is
 * unavailable and why — never a fabricated zero.
 */
final class PageRenderer {
    static final int TEXT = 0xFFE8E8E8;
    static final int DIM = 0xFF9AA0A6;
    static final int GOOD = 0xFF7BD88F;
    static final int WARN = 0xFFE0B34C;
    static final int BAD = 0xFFE06C5C;
    static final int HEADER = 0xFFF2C66D;

    private PageRenderer() {
    }

    static void renderPage(LagLensScreen screen, DrawContext ctx, String page, int x, int y, int w,
            int h) {
        MetricsSnapshot s = screen.session().lastSnapshot();
        Ctx c = new Ctx(screen, ctx, s, x, y, w, h);
        switch (page) {
            case "overview" -> renderOverview(c);
            case "bottlenecks" -> renderBottlenecks(c);
            case "terrain" -> renderTerrain(c);
            case "entities" -> renderEntities(c);
            case "rendering" -> renderRendering(c);
            case "network" -> renderNetwork(c);
            case "memory" -> renderMemory(c);
            case "server" -> renderServer(c);
            case "mods" -> renderMods(c);
            case "capture" -> renderCapture(c);
            case "settings" -> renderSettings(c);
            default -> c.body("Unknown page.", DIM);
        }
    }

    /** Cursor + viewport-culling helper shared by all pages. */
    private static final class Ctx {
        final LagLensScreen screen;
        final DrawContext ctx;
        final MetricsSnapshot s;
        final int x;
        final int w;
        int y;
        final int bottom;
        int line;

        Ctx(LagLensScreen screen, DrawContext ctx, MetricsSnapshot s, int x, int y, int w, int h) {
            this.screen = screen;
            this.ctx = ctx;
            this.s = s;
            this.x = x;
            this.y = y;
            this.w = w;
            this.bottom = y + h;
            this.line = screen.scroll();
        }

        void skip(int lines) {
            line += lines;
        }

        private boolean visible() {
            return y + 10 <= bottom;
        }

        void header(String text) {
            if (line-- <= 0 && visible()) {
                ctx.drawTextWithShadow(screen.textRenderer(), Text.literal(text), x, y, HEADER);
                y += 12;
            } else if (line < 0) {
                y += 0;
            }
            gap(2);
        }

        void body(String text, int color) {
            for (String part : split(text, 110)) {
                if (line-- <= 0) {
                    if (!visible()) {
                        return;
                    }
                    ctx.drawTextWithShadow(screen.textRenderer(), Text.literal(part), x, y,
                            color);
                    y += 10;
                }
            }
        }

        void row(String label, String value, int color) {
            body(label + ": " + value, color);
        }

        void gap(int px) {
            y += px;
        }

        private static List<String> split(String text, int max) {
            List<String> out = new ArrayList<>();
            String rest = text;
            while (rest.length() > max) {
                int cut = rest.lastIndexOf(' ', max);
                if (cut < max / 2) {
                    cut = max;
                }
                out.add(rest.substring(0, cut));
                rest = rest.substring(cut).stripLeading();
            }
            out.add(rest);
            return out;
        }
    }

    // ---- Overview ----

    private static void renderOverview(Ctx c) {
        MetricsSnapshot s = c.s;
        c.header("Overview — last " + (s.windowMs / 1000) + " s window");
        if (s.frameSampleCount == 0) {
            c.body("No frame samples yet (open a world; the title screen renders no HUD pass).",
                    WARN);
            c.body("FPS (instant): " + fpsOf(c), TEXT);
            return;
        }
        c.row("FPS (instant) / from avg frame", fpsOf(c) + " / " + fpsFromAvg(s), TEXT);
        c.row("Frame avg / P50 / P95 / P99 / max (ms)",
                Format.ms(s.avgFrameMs) + " / " + Format.ms(s.p50FrameMs) + " / "
                        + Format.ms(s.p95FrameMs) + " / " + Format.ms(s.p99FrameMs) + " / "
                        + Format.ms(s.maxFrameMs),
                TEXT);
        c.row("Spikes over " + c.screen.config().spikeWarnMs + " ms", s.spikeCount + " in "
                + s.frameSampleCount + " samples", s.spikeCount == 0 ? GOOD : WARN);
        drawFrameGraph(c);
        c.gap(2);
        c.row("Heap",
                s.memoryAvailable ? Format.bytes(s.heapUsedBytes) + " / "
                        + Format.bytes(s.heapMaxBytes) + " max" : "unavailable", TEXT);
        c.row("Entities",
                s.entityAvailable ? String.valueOf(s.entityCount) : "unavailable", TEXT);
        c.row("Chunks",
                s.chunksAvailable ? s.loadedChunks + " loaded, ~" + s.chunkEventsPerMin + " events/min"
                        : "unavailable",
                TEXT);
        c.row("Ping", s.networkAvailable ? Math.round(s.pingMs) + " ms" : "unavailable", TEXT);
        c.row("Server tick",
                s.serverTickAvailable ? Format.ms(s.serverTickMs) + " (" + s.serverTickSource + ")"
                        : "unavailable (no integrated server or companion)",
                TEXT);
        c.row("Capture",
                c.screen.session().capture().state() == CaptureSession.State.RECORDING
                        ? "RECORDING " + c.screen.session().capture().snapshotCount() + " snapshots"
                        : "idle (press C)",
                TEXT);
        c.gap(4);
        c.header("Most likely bottleneck");
        List<Finding> findings = c.screen.session().findings();
        if (findings.isEmpty()) {
            c.body("No findings yet.", DIM);
        } else {
            Finding top = findings.get(0);
            c.body(top.title() + " [" + top.severity().label() + ", "
                    + top.confidence().label() + " confidence, " + top.evidence().label() + "]",
                    severityColor(top));
            c.body(top.symptom(), DIM);
        }
    }

    private static String fpsOf(Ctx c) {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            return String.valueOf(client.getCurrentFps());
        } catch (Exception e) {
            return "n/a";
        }
    }

    private static String fpsFromAvg(MetricsSnapshot s) {
        double fps = com.laglens.metrics.FrameStats.frameMsToFps(s.avgFrameMs);
        return Double.isFinite(fps) ? String.format("%.0f", fps) : "n/a";
    }

    private static void drawFrameGraph(Ctx c) {
        double[] samples = c.screen.session().frames().samples();
        if (samples.length == 0) {
            return;
        }
        int n = Math.min(samples.length, 160);
        double ceil = Math.max(c.screen.config().spikeSevereMs * 1.2, 50.0);
        int gw = Math.min(c.w, n * 2);
        int gh = 44;
        if (c.line-- > 0) {
            c.gap(0);
            return;
        }
        if (c.y + gh > c.bottom) {
            return;
        }
        int gx = c.x;
        int gy = c.y;
        c.ctx.fill(gx, gy, gx + gw, gy + gh, 0xFF0B0D10);
        double warnFrac = c.screen.config().spikeWarnMs / ceil;
        double sevFrac = c.screen.config().spikeSevereMs / ceil;
        c.ctx.fill(gx, gy + (int) (gh * (1 - warnFrac)), gx + gw,
                gy + (int) (gh * (1 - warnFrac)) + 1, 0x66E0B34C);
        c.ctx.fill(gx, gy + (int) (gh * (1 - sevFrac)), gx + gw,
                gy + (int) (gh * (1 - sevFrac)) + 1, 0x66E06C5C);
        for (int i = 0; i < n; i++) {
            double v = samples[samples.length - n + i];
            int bh = (int) Math.min(gh, gh * (v / ceil));
            int color = v > c.screen.config().spikeSevereMs ? BAD
                    : v > c.screen.config().spikeWarnMs ? WARN : GOOD;
            c.ctx.fill(gx + i * 2, gy + gh - bh, gx + i * 2 + 1, gy + gh, 0xFF000000 | color);
        }
        c.y += gh + 2;
        c.ctx.drawTextWithShadow(c.screen.textRenderer(),
                Text.literal("Frame ms, newest right. Yellow = warn, red = severe."), gx, c.y, DIM);
        c.y += 10;
    }

    // ---- Bottlenecks ----

    private static void renderBottlenecks(Ctx c) {
        c.header("Bottlenecks — ranked by severity");
        List<Finding> findings = c.screen.session().findings();
        if (findings.isEmpty()) {
            c.body("No findings. Findings appear after ~30 frame samples.", DIM);
            return;
        }
        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            String head = (i + 1) + ". " + f.title() + " [" + f.severity().label() + "/"
                    + f.confidence().label() + "/" + f.evidence().label() + "]";
            if (c.line-- <= 0) {
                if (c.y + 10 > c.bottom) {
                    return;
                }
                int y0 = c.y;
                c.ctx.drawTextWithShadow(c.screen.textRenderer(), Text.literal(head), c.x, c.y,
                        severityColor(f));
                c.y += 10;
                c.screen.addClickTarget(c.x, y0, c.x + c.w, c.y, -1, i);
            }
            if (c.screen.expandedFinding() == i) {
                c.body("Symptom: " + f.symptom(), TEXT);
                for (String e : f.evidenceLines()) {
                    c.body("  - " + e, DIM);
                }
                c.body("Unknowns: " + f.unknowns(), DIM);
                c.body("Next: " + f.nextStep(), GOOD);
                c.body("Detail page: " + LagLensScreen.titleOf(f.page())
                        + " (use Up/Down to navigate)", DIM);
                c.gap(2);
            }
        }
    }

    private static int severityColor(Finding f) {
        return switch (f.severity()) {
            case CRITICAL -> BAD;
            case WARN -> WARN;
            case INFO -> GOOD;
        };
    }

    // ---- Terrain ----

    private static void renderTerrain(Ctx c) {
        MetricsSnapshot s = c.s;
        c.header("Terrain & chunks");
        if (!s.chunksAvailable) {
            c.body("No chunk data (open a world).", WARN);
        } else {
            c.row("Loaded client chunks", String.valueOf(s.loadedChunks), TEXT);
            c.row("Load/unload events", "~" + s.chunkEventsPerMin + "/min (trailing 2 min)",
                    TEXT);
            c.row("Spike coincidences (500 ms radius)", s.chunkSpikeCoincidences + " of "
                    + s.spikeCount + " spikes", TEXT);
        }
        c.gap(4);
        c.header("What each stage means");
        c.body("Directly measured: client loaded-chunk count + load/unload events.", TEXT);
        c.body("Unavailable by design: server-side generation cost, mesh-rebuild CPU time, "
                + "chunk upload timing — the vanilla pipeline exposes none of these without "
                + "renderer-specific integration.", WARN);
        c.body("A chunk packet arriving is not proof your CPU generated terrain; delivery "
                + "delay, mesh rebuild, allocation, or render upload can each be the slow stage.",
                DIM);
    }

    // ---- Entities ----

    private static void renderEntities(Ctx c) {
        MetricsSnapshot s = c.s;
        LagLensSession session = c.screen.session();
        c.header("Entities");
        if (!s.entityAvailable) {
            c.body("No entity data (open a world).", WARN);
        } else {
            EntityCollector.Census census = session.entities().census();
            c.row("Loaded client entities", s.entityCount + " (" + census.players() + " players)",
                    TEXT);
            if ("DETAILED".equals(session.config().monitoringLevel)) {
                for (Map.Entry<String, Integer> e : session.entities().topTypes(8)) {
                    c.body("  " + e.getKey() + " x" + e.getValue(), DIM);
                }
            } else {
                c.body("Per-type breakdown needs DETAILED monitoring (config).", DIM);
            }
            c.gap(2);
            c.header("Block entities (sampled, radius 3 chunks)");
            if (!s.blockEntityAvailable) {
                c.body("Unavailable in this sample.", WARN);
            } else {
                c.row("Sampled block entities", String.valueOf(s.blockEntityCount), TEXT);
            }
        }
        c.gap(2);
        c.body("LagLens counts populations; it never assumes a large count is expensive and "
                + "never touches entities, updates, or server state.", GOOD);
        c.body("Continuous per-entity timings are intentionally not collected (expensive and "
                + "not attributable per mod).", DIM);
    }

    // ---- Rendering ----

    private static void renderRendering(Ctx c) {
        MetricsSnapshot s = c.s;
        c.header("Rendering");
        c.row("FPS (instant)", fpsOf(c), TEXT);
        c.row("Frame P50 / P95 / P99",
                Format.ms(s.p50FrameMs) + " / " + Format.ms(s.p95FrameMs) + " / "
                        + Format.ms(s.p99FrameMs),
                TEXT);
        intvd(c);
        c.gap(2);
        c.header("Workload indicators");
        c.row("Shader/renderer mods",
                describePresence(c, new String[]{"sodium", "iris", "immediatelyfast",
                        "entityculling"}),
                TEXT);
        c.body("GPU time is only shown with a reliable measurement source; none is bundled, "
                + "so CPU-vs-GPU separation is stated as unknown unless an integration provides it.",
                WARN);
        c.body("Resource reload events are not yet tracked; a reload hitch appears as an "
                + "isolated spike.", DIM);
    }

    private static void intvd(Ctx c) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            int vd = ClientWorldReader.renderDistance(client);
            c.row("Render distance", vd >= 0 ? vd + " chunks" : "unavailable", TEXT);
        } catch (Exception e) {
            c.row("Render distance", "unavailable", TEXT);
        }
    }

    private static String describePresence(Ctx c, String[] ids) {
        StringBuilder sb = new StringBuilder();
        for (ModInventoryCollector.IntegrationStatus st : c.screen.session().modInventory()
                .integrations()) {
            for (String id : ids) {
                if (st.id().equals(id) && st.active()) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(id);
                }
            }
        }
        return sb.length() == 0 ? "vanilla pipeline" : sb.toString();
    }

    // ---- Network ----

    private static void renderNetwork(Ctx c) {
        MetricsSnapshot s = c.s;
        c.header("Network (client-observable only)");
        if (!s.networkAvailable) {
            c.body("No latency data (not connected to a server).", WARN);
        } else {
            c.row("Ping (round-trip)", Math.round(s.pingMs) + " ms", TEXT);
            c.row("Jitter (mean step variation)",
                    Double.isFinite(s.pingJitterMs) ? Math.round(s.pingJitterMs) + " ms"
                            : "unavailable",
                    TEXT);
            drawPingGraph(c);
        }
        c.gap(2);
        c.row("Disconnects this session",
                String.valueOf(c.screen.session().network().disconnects()), TEXT);
        c.body("Packet inspection: not instrumented — counts, sizes and timings only when a "
                + "safe hook exists. No payloads are ever collected.", WARN);
        c.body("Application-level observation cannot measure packet loss; loss is never claimed.",
                DIM);
    }

    private static void drawPingGraph(Ctx c) {
        double[] samples = c.screen.session().network().history();
        if (samples.length < 2) {
            return;
        }
        int n = Math.min(samples.length, 120);
        int gh = 30;
        if (c.line-- > 0) {
            return;
        }
        if (c.y + gh > c.bottom) {
            return;
        }
        double max = 1.0;
        for (int i = samples.length - n; i < samples.length; i++) {
            max = Math.max(max, samples[i]);
        }
        int gw = Math.min(c.w, n * 2);
        c.ctx.fill(c.x, c.y, c.x + gw, c.y + gh, 0xFF0B0D10);
        for (int i = 0; i < n; i++) {
            int bh = (int) Math.min(gh, gh * (samples[samples.length - n + i] / max));
            c.ctx.fill(c.x + i * 2, c.y + gh - bh, c.x + i * 2 + 1, c.y + gh, 0xFF58A6D8);
        }
        c.y += gh + 2;
    }

    // ---- Memory ----

    private static void renderMemory(Ctx c) {
        MetricsSnapshot s = c.s;
        c.header("Memory & GC");
        if (!s.memoryAvailable) {
            c.body("Memory beans unavailable on this JVM.", WARN);
            return;
        }
        c.row("Heap used", Format.bytes(s.heapUsedBytes) + " of "
                + Format.bytes(s.heapMaxBytes) + " max (" + Format.bytes(s.heapCommittedBytes)
                + " committed)", TEXT);
        c.row("GC in window", s.gcCountDelta + " collections, " + Format.ms(s.gcTimeMsDelta), TEXT);
        long lastPause = c.screen.session().memory().lastGcPauseMs();
        c.row("Last GC pause", lastPause >= 0 ? lastPause + " ms" : "none observed", TEXT);
        c.gap(2);
        c.header("Reading memory correctly");
        c.body("Used heap, committed, max heap, and GC pauses are different things; a full "
                + "heap that drops after GC is healthy, not a leak.", TEXT);
        c.body("Only sustained post-GC growth across captures is flagged for investigation — "
                + "never a single high reading.", GOOD);
        c.body("LagLens never forces GC and never changes JVM flags or heap size.", DIM);
    }

    // ---- Server ----

    private static void renderServer(Ctx c) {
        MetricsSnapshot s = c.s;
        c.header("Server performance");
        if (!s.serverTickAvailable) {
            c.body("Unavailable: no integrated server is running and no companion is "
                    + "connected. Remote tick internals are invisible to a client-only mod and "
                    + "are never guessed from ping.", WARN);
            c.body("Singleplayer / LAN worlds publish integrated tick data automatically. "
                    + "Remote servers need the optional companion (protocol v1, documented in "
                    + "docs/SERVER_PROTOCOL.md).", DIM);
            return;
        }
        c.row("Average tick (" + s.serverTickSource + ")", Format.ms(s.serverTickMs)
                + " / 50 ms budget", s.serverTickMs > 50 ? BAD : GOOD);
        c.body("An average tick time and sustained TPS are not interchangeable; sustained "
                + "overload is judged over minutes, not one sample.", DIM);
    }

    // ---- Mods ----

    private static void renderMods(Ctx c) {
        LagLensSession session = c.screen.session();
        c.header("Mods (" + session.modInventory().modCount() + " installed)");
        c.body("Presence is not cost: Java exposes no per-mod CPU time, and time in shared "
                + "Minecraft code is never blamed on a mod for being loaded.", WARN);
        c.gap(2);
        c.header("Integrations");
        for (ModInventoryCollector.IntegrationStatus st : session.modInventory().integrations()) {
            c.row((st.active() ? "[active] " : "[absent] ") + st.id(), st.detail(),
                    st.active() ? GOOD : DIM);
        }
        c.gap(2);
        c.header("Installed mods");
        List<ModInventoryCollector.ModEntry> mods = session.modInventory().mods();
        int shown = 0;
        for (ModInventoryCollector.ModEntry m : mods) {
            if (shown >= 40) {
                break;
            }
            c.body(m.id() + " " + m.version(), DIM);
            shown++;
        }
        if (mods.size() > shown) {
            c.body("... and " + (mods.size() - shown) + " more (full list in exported reports).",
                    DIM);
        }
    }

    // ---- Capture ----

    private static void renderCapture(Ctx c) {
        LagLensSession session = c.screen.session();
        CaptureSession capture = session.capture();
        c.header("Capture & reports");
        c.row("State", capture.state().name()
                + (capture.state() == CaptureSession.State.RECORDING
                        ? " (" + capture.mode() + ", " + capture.snapshotCount() + " snapshots)"
                        : ""),
                TEXT);
        c.body("1. Press C (or the Capture button). 2. Reproduce the lag. 3. Stop with C. "
                + "4. Press E to export JSON + Markdown into "
                + session.config().reportDir + "/.", TEXT);
        c.body("Recordings are bounded (" + CaptureSession.MAX_SNAPSHOTS
                + " snapshots max) with a rolling pre-buffer of "
                + session.config().rollingBufferSec + " s; nothing uploads anywhere — "
                + "inspect the files, then share them yourself.", DIM);
        c.row("Retention", "last " + session.config().reportRetention + " reports kept", TEXT);
        c.row("Environment metadata",
                session.config().includeEnvMetadata ? "included"
                        + (session.config().privacyStripEnv ? " (host/user stripped)" : "")
                        : "excluded",
                TEXT);
    }

    // ---- Settings ----

    private static void renderSettings(Ctx c) {
        LagLensSession session = c.screen.session();
        c.header("Settings");
        double overhead = session.meanOverheadMicros();
        c.row("LagLens self-overhead (mean hook cost)",
                Double.isFinite(overhead) ? String.format("%.1f us", overhead) : "measuring...",
                TEXT);
        c.row("Profile", session.config().monitoringLevel + " (LIGHT totals / DETAILED by-type)",
                TEXT);
        c.row("History", session.config().historySeconds + " s rolling", TEXT);
        c.row("Spike thresholds",
                "warn " + session.config().spikeWarnMs + " ms, severe "
                        + session.config().spikeSevereMs + " ms, recurring x"
                        + session.config().recurringSpikeMinimum,
                TEXT);
        c.row("Auto-capture",
                session.config().autoCaptureOnSevereSpike
                        ? "on severe spike, " + session.config().autoCaptureSec + " s"
                        : "off",
                TEXT);
        c.row("Reports",
                session.config().reportDir + "/ (keep " + session.config().reportRetention + ")",
                TEXT);
        c.gap(2);
        c.body("Edit config/laglens.json while the game is closed; invalid values are clamped "
                + "to safe defaults on load. Full option reference: docs/CONFIG.md.", DIM);
        c.body("Monitoring stays lightweight by design: 1 Hz samplers, bounded buffers, no "
                + "per-frame allocation on hot paths.", DIM);
    }
}
