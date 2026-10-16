package com.laglens;

import com.laglens.capture.CaptureSession;
import com.laglens.capture.ReportWriter;
import com.laglens.client.ClientWorldReader;
import com.laglens.collectors.ModInventoryCollector;
import com.laglens.command.LagLensCommands;
import com.laglens.config.LagLensConfig;
import com.laglens.integration.IntegrationRegistry;
import com.laglens.session.LagLensSession;
import com.laglens.ui.LagLensScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Client entrypoint: wires collectors, commands, keybind and UI to Fabric events.
 *
 * <p>LagLens is strictly client-side ({@code environment: client}) and works
 * with no server companion. No mixins are used in this release: frame timing
 * comes from the HUD-render event, world facts from guarded client accessors,
 * and everything else from JVM management beans.
 */
public final class LagLensMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("LagLens");

    private static LagLensSession session;
    private static LagLensConfig config;
    private static Path gameDir;
    private static KeyBinding openKey;
    private static final ReportWriter REPORTS = new ReportWriter();
    private static Future<ReportWriter.ExportResult> pendingExport;
    /** Auto-stopped capture waiting for an export slot; retried every tick, never dropped. */
    private static CaptureSession.CaptureResult queuedAutoResult;

    @Override
    public void onInitializeClient() {
        gameDir = FabricLoader.getInstance().getGameDir();
        config = LagLensConfig.load(gameDir.resolve("config/laglens.json"));
        session = new LagLensSession(config);
        session.setWorldReader(new ClientWorldReader());
        publishInventory();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.laglens.open",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F9, "category.laglens"));

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (session != null) {
                session.onFrame();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (session == null) {
                return;
            }
            session.onClientTick(client);
            while (openKey.wasPressed()) {
                openDashboard(client);
            }
            pollAutoStop(client);
            pollPendingExport(client);
        });
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (session != null && session.config().enableChunkEvents) {
                session.chunks().recordLoad();
            }
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            if (session != null && session.config().enableChunkEvents) {
                session.chunks().recordUnload();
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (session != null) {
                publishInventory();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (session != null) {
                session.network().recordDisconnect(System.currentTimeMillis());
                session.network().reset();
                session.serverTick().markUnavailable();
                session.chunks().markUnavailable();
                session.entities().markUnavailable();
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> REPORTS.shutdown());

        ClientCommandRegistrationCallback.EVENT
                .register((dispatcher, registryAccess) -> LagLensCommands.register(dispatcher));

        LOGGER.info("LagLens initialized (client-only, no mixins).");
    }

    private static void publishInventory() {
        List<ModInventoryCollector.ModEntry> entries = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            entries.add(new ModInventoryCollector.ModEntry(mod.getMetadata().getId(),
                    mod.getMetadata().getVersion().getFriendlyString(),
                    mod.getMetadata().getName()));
        }
        session.modInventory().publishMods(entries);
        session.modInventory().publishIntegrations(IntegrationRegistry.check(new IntegrationRegistry.ModQuery() {
            @Override
            public boolean isModLoaded(String id) {
                return FabricLoader.getInstance().isModLoaded(id);
            }

            @Override
            public String versionOf(String id) {
                return FabricLoader.getInstance().getModContainer(id)
                        .map(c -> c.getMetadata().getVersion().getFriendlyString())
                        .orElse("unknown");
            }
        }));
    }

    public static void openDashboard(MinecraftClient client) {
        if (client == null || session == null) {
            LOGGER.warn("LagLens dashboard requested but client/session unavailable.");
            return;
        }
        // Gate log: proves this build is loaded and the command reached us.
        LOGGER.info("LagLens dashboard requested.");
        if (!client.isOnThread()) {
            client.execute(() -> openDashboard(client));
            return;
        }
        try {
            client.setScreen(new LagLensScreen(session));
            // Gate log: proves setScreen returned without throwing.
            LOGGER.info("LagLens dashboard opened.");
        } catch (Exception e) {
            // Never fail silently: the exact cause goes to the log and to chat.
            LOGGER.error("LagLens failed to open dashboard", e);
            try {
                if (client.inGameHud != null) {
                    client.inGameHud.getChatHud().addMessage(Text.literal(
                            "[LagLens] Could not open dashboard (" + e.getClass().getSimpleName()
                                    + ": " + e.getMessage() + "). See latest.log and report this."));
                }
            } catch (Exception ignored) {
                // Chat itself unavailable; the log above is the record.
            }
        }
    }

    /**
     * Picks up captures that hit their auto-stop deadline: announces them in
     * chat and exports them as soon as no other export is running. Retried
     * every tick until the export starts, so the recording is never lost.
     */
    private static void pollAutoStop(MinecraftClient client) {
        if (queuedAutoResult == null) {
            queuedAutoResult = session.takeAutoStopped();
        }
        if (queuedAutoResult == null) {
            return;
        }
        Future<ReportWriter.ExportResult> pending = pendingExport;
        if (pending != null && !pending.isDone()) {
            return;
        }
        CaptureSession.CaptureResult result = queuedAutoResult;
        queuedAutoResult = null;
        pendingExport = REPORTS.exportAsync(gameDir, config, result,
                com.laglens.command.LagLensCommands::environment, session.modInventory().mods(),
                session.limitations());
        if (client.player != null) {
            client.inGameHud.getChatHud().addMessage(Text.literal("[LagLens] Auto-capture finished ("
                    + result.snapshots().size() + " snapshots over " + (result.durationMs() / 1000)
                    + " s); exporting report…"));
        }
    }

    private static void pollPendingExport(MinecraftClient client) {
        Future<ReportWriter.ExportResult> future = pendingExport;
        if (future == null || !future.isDone()) {
            return;
        }
        pendingExport = null;
        try {
            ReportWriter.ExportResult result = future.get();
            if (result.ok()) {
                client.inGameHud.getChatHud().addMessage(Text.literal(
                        "[LagLens] Report saved: " + result.json().getFileName()));
            } else {
                client.inGameHud.getChatHud().addMessage(
                        Text.literal("[LagLens] Export failed: " + result.error()));
                LOGGER.warn("LagLens export failed: {}", result.error());
            }
        } catch (Exception e) {
            client.inGameHud.getChatHud()
                    .addMessage(Text.literal("[LagLens] Export failed: " + e.getMessage()));
        }
    }

    public static LagLensSession session() {
        return session;
    }

    public static LagLensConfig config() {
        return config;
    }

    public static Path gameDir() {
        return gameDir;
    }

    public static ReportWriter reports() {
        return REPORTS;
    }

    /**
     * Stops an in-progress recording (if any) and exports the result, or
     * exports the live view when nothing was recorded. Returns false when
     * another export is still running, so concurrent exports can never orphan
     * each other's completion feedback.
     */
    public static boolean stopCaptureAndExport() {
        Future<ReportWriter.ExportResult> pending = pendingExport;
        if (pending != null && !pending.isDone()) {
            return false;
        }
        CaptureSession.CaptureResult result = session.capture().stop(session.findings());
        if (result == null || result.snapshots().isEmpty()) {
            CaptureSession live = new CaptureSession();
            live.start(CaptureSession.Mode.LIGHT, System.currentTimeMillis(), 0L);
            live.record(session.lastSnapshot());
            result = live.stop(session.findings());
        }
        pendingExport = REPORTS.exportAsync(gameDir, config, result,
                com.laglens.command.LagLensCommands::environment, session.modInventory().mods(),
                session.limitations());
        return true;
    }
}
