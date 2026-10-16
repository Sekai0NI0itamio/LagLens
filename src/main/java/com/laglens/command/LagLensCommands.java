package com.laglens.command;

import com.laglens.LagLensMod;
import com.laglens.capture.CaptureSession;
import com.laglens.capture.ReportWriter;
import com.laglens.metrics.Clock;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import java.net.InetAddress;

/**
 * Client-side {@code /laglens} command tree (no server plugin required).
 * <ul>
 *   <li>{@code /laglens} — open the dashboard.</li>
 *   <li>{@code /laglens status} — one-line chat summary.</li>
 *   <li>{@code /laglens capture} — show state; {@code start}/{@code detailed}/{@code stop}.</li>
 *   <li>{@code /laglens export} — stop (if recording) and write JSON + Markdown.</li>
 *   <li>{@code /laglens config} — where the config lives and the active profile.</li>
 * </ul>
 */
public final class LagLensCommands {
    private LagLensCommands() {
    }

    public static void register(
            CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("laglens")
                .executes(ctx -> {
                    LagLensMod.openDashboard(ctx.getSource().getClient());
                    return 1;
                })
                .then(ClientCommandManager.literal("status").executes(ctx -> {
                    LagLensSession session = LagLensMod.session();
                    for (String line : session.statusLines()) {
                        ctx.getSource().sendFeedback(Text.literal("[LagLens] " + line));
                    }
                    return 1;
                }))
                .then(ClientCommandManager.literal("capture")
                        .executes(ctx -> {
                            reportCaptureState(ctx.getSource());
                            return 1;
                        })
                        .then(ClientCommandManager.literal("start").executes(ctx -> {
                            return startCapture(ctx.getSource(), CaptureSession.Mode.LIGHT);
                        }))
                        .then(ClientCommandManager.literal("detailed").executes(ctx -> {
                            return startCapture(ctx.getSource(), CaptureSession.Mode.DETAILED);
                        }))
                        .then(ClientCommandManager.literal("stop").executes(ctx -> {
                            return stopCapture(ctx.getSource());
                        })))
                .then(ClientCommandManager.literal("export").executes(ctx -> {
                    return exportReport(ctx.getSource());
                }))
                .then(ClientCommandManager.literal("config").executes(ctx -> {
                    FabricClientCommandSource src = ctx.getSource();
                    src.sendFeedback(Text.literal("[LagLens] Config: "
                            + LagLensMod.gameDir().resolve("config/laglens.json")));
                    src.sendFeedback(Text.literal("[LagLens] Profile: "
                            + LagLensMod.config().monitoringLevel + ", history "
                            + LagLensMod.config().historySeconds + " s, spike warn "
                            + LagLensMod.config().spikeWarnMs + " ms"));
                    return 1;
                })));
    }

    private static void reportCaptureState(FabricClientCommandSource src) {
        CaptureSession capture = LagLensMod.session().capture();
        if (capture.state() == CaptureSession.State.RECORDING) {
            src.sendFeedback(Text.literal("[LagLens] Recording (" + capture.mode() + ", "
                    + capture.snapshotCount() + " snapshots). Use /laglens capture stop."));
        } else {
            src.sendFeedback(Text.literal(
                    "[LagLens] Idle. Use /laglens capture start (light) or /laglens capture detailed."));
        }
    }

    private static int startCapture(FabricClientCommandSource src, CaptureSession.Mode mode) {
        CaptureSession capture = LagLensMod.session().capture();
        boolean started = capture.start(mode, Clock.milliTime(),
                LagLensMod.config().autoCaptureSec * 1000L);
        src.sendFeedback(Text.literal(started
                ? "[LagLens] Recording started (" + mode + "). Reproduce the issue, then /laglens capture stop."
                : "[LagLens] Already recording. Use /laglens capture stop first."));
        return 1;
    }

    private static int stopCapture(FabricClientCommandSource src) {
        LagLensSession session = LagLensMod.session();
        if (session.capture().state() != CaptureSession.State.RECORDING) {
            src.sendFeedback(Text.literal("[LagLens] No recording in progress."));
            return 0;
        }
        if (!LagLensMod.stopCaptureAndExport()) {
            src.sendFeedback(Text.literal("[LagLens] An export is already running; try again shortly."));
            return 0;
        }
        src.sendFeedback(Text.literal(
                "[LagLens] Recording stopped and exporting; the path will print in chat."));
        return 1;
    }

    private static int exportReport(FabricClientCommandSource src) {
        if (!LagLensMod.stopCaptureAndExport()) {
            src.sendFeedback(Text.literal("[LagLens] An export is already running; try again shortly."));
            return 0;
        }
        src.sendFeedback(Text.literal("[LagLens] Exporting; the path will print in chat."));
        return 1;
    }

    /** Best-effort environment facts; every field may be "unavailable". */
    public static ReportWriter.EnvironmentInfo environment() {
        String mc = modVersion("minecraft");
        String loader = modVersion("fabricloader");
        String self = modVersion("laglens");
        String java = System.getProperty("java.version", "unavailable");
        String os = System.getProperty("os.name", "?") + " "
                + System.getProperty("os.version", "?") + " ("
                + System.getProperty("os.arch", "?") + ")";
        String cpu = Runtime.getRuntime().availableProcessors() + " logical cores";
        long maxHeap = Runtime.getRuntime().maxMemory();
        String heap = maxHeap <= 0 ? "unavailable"
                : (maxHeap / (1024 * 1024)) + " MB max heap";
        String user = System.getProperty("user.name", "unavailable");
        String host = "unavailable";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            // Host lookup is best-effort; reports mark it unavailable.
        }
        return new ReportWriter.EnvironmentInfo(mc, loader, self, java, os, cpu, null, heap, user,
                host);
    }

    private static String modVersion(String id) {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(id)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("unavailable");
        } catch (Exception e) {
            return "unavailable";
        }
    }
}
