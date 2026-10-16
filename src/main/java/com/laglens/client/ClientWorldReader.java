package com.laglens.client;

import com.laglens.session.LagLensSession;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reads live client state into the session's collectors at the 1 Hz cadence.
 *
 * <p>Every probe is independently guarded: if a mapping or runtime shape
 * differs from expectations, that probe marks its metric unavailable instead
 * of throwing, so one failed probe can never break the dashboard or the tick
 * loop. All iteration is bounded (entity cap, chunk spiral radius) to keep the
 * sampler cheap.
 */
public final class ClientWorldReader implements LagLensSession.MinecraftWorldReader {
    private static final int MAX_ENTITIES_SCANNED = 100_000;
    private static final int CHUNK_SAMPLE_RADIUS = 3;

    @Override
    public void sample(LagLensSession session, MinecraftClient client) {
        long nowMs = System.currentTimeMillis();
        ClientWorld world = client.world;

        sampleEntities(session, world, nowMs);
        sampleBlockEntities(session, client, world, nowMs);
        sampleChunks(session, world);
        sampleNetwork(session, client, nowMs);
        sampleServerTick(session, client);
    }

    private void sampleEntities(LagLensSession session, ClientWorld world, long nowMs) {
        if (world == null) {
            session.entities().markUnavailable();
            return;
        }
        try {
            int total = 0;
            int players = world.getPlayers().size();
            Map<String, Integer> byType = new HashMap<>();
            boolean detailed = "DETAILED".equals(session.config().monitoringLevel);
            for (Entity entity : world.getEntities()) {
                if (entity == null) {
                    continue;
                }
                total++;
                if (detailed) {
                    Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
                    byType.merge(id == null ? "unknown" : id.toString(), 1, Integer::sum);
                }
                if (total >= MAX_ENTITIES_SCANNED) {
                    break;
                }
            }
            session.publishEntityCensus(nowMs, total, players, byType, detailed);
        } catch (Exception e) {
            session.entities().markUnavailable();
        }
    }

    private void sampleBlockEntities(LagLensSession session, MinecraftClient client,
            ClientWorld world, long nowMs) {
        if (world == null || client.player == null
                || !session.config().enableEntitySampling) {
            return;
        }
        try {
            BlockPos origin = client.player.getBlockPos();
            int baseCx = origin.getX() >> 4;
            int baseCz = origin.getZ() >> 4;
            int total = 0;
            Map<String, Integer> byType = new HashMap<>();
            boolean detailed = "DETAILED".equals(session.config().monitoringLevel);
            for (int dx = -CHUNK_SAMPLE_RADIUS; dx <= CHUNK_SAMPLE_RADIUS; dx++) {
                for (int dz = -CHUNK_SAMPLE_RADIUS; dz <= CHUNK_SAMPLE_RADIUS; dz++) {
                    WorldChunk chunk = world.getChunkManager()
                            .getChunk(baseCx + dx, baseCz + dz, ChunkStatus.FULL, false);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (be == null || be.isRemoved()) {
                            continue;
                        }
                        total++;
                        if (detailed && be.getType() != null) {
                            Identifier id = Registries.BLOCK_ENTITY_TYPE.getId(be.getType());
                            byType.merge(id == null ? "unknown" : id.toString(), 1, Integer::sum);
                        }
                    }
                }
            }
            session.publishBlockEntities(nowMs, total, byType, detailed);
        } catch (Exception e) {
            // Leave the previous census in place; a failed sample is not data.
        }
    }

    private void sampleChunks(LagLensSession session, ClientWorld world) {
        if (world == null) {
            session.chunks().markUnavailable();
            return;
        }
        try {
            session.chunks().setLoadedChunks(world.getChunkManager().getLoadedChunkCount());
        } catch (Exception e) {
            session.chunks().markUnavailable();
        }
    }

    private void sampleNetwork(LagLensSession session, MinecraftClient client, long nowMs) {
        try {
            if (client.getNetworkHandler() == null || client.player == null) {
                session.network().reset();
                return;
            }
            UUID uuid = client.player.getUuid();
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(uuid);
            if (entry == null) {
                session.network().reset();
                return;
            }
            session.network().recordPing(entry.getLatency());
        } catch (Exception e) {
            session.network().reset();
        }
    }

    private void sampleServerTick(LagLensSession session, MinecraftClient client) {
        try {
            IntegratedServer server = client.getServer();
            if (server == null) {
                session.serverTick().markUnavailable();
                return;
            }
            session.serverTick().publishIntegrated(server.getAverageTickTime());
        } catch (Exception e) {
            session.serverTick().markUnavailable();
        }
    }

    /** Render distance when readable; -1 when the option is unavailable. */
    public static int renderDistance(MinecraftClient client) {
        try {
            return client.options.getViewDistance().getValue();
        } catch (Exception e) {
            return -1;
        }
    }

    /** True when the world pointer satisfies the given class without touching internals. */
    public static boolean inWorld(MinecraftClient client) {
        return client.world != null && client.player != null;
    }

    /** World reference for callers that need one extra guarded fact. */
    public static World worldOf(MinecraftClient client) {
        return client.world;
    }
}
