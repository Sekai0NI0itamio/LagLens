package com.laglens;

import com.laglens.collectors.ChunkCollector;
import com.laglens.collectors.EntityCollector;
import com.laglens.collectors.ModInventoryCollector;
import com.laglens.collectors.NetworkCollector;
import com.laglens.collectors.ServerTickCollector;
import com.laglens.integration.IntegrationRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Collector contracts: bounded buffers, availability flags, honest defaults.
 * (Live Minecraft sampling itself is exercised in-game; CI covers the logic.)
 */
class CollectorLogicTest {
    @Test
    void chunkEventsAreWindowed() throws Exception {
        ChunkCollector c = new ChunkCollector();
        assertEquals(-1, c.eventsPerMinute());
        c.setLoadedChunks(200);
        c.recordLoad();
        c.recordUnload();
        assertTrue(c.eventsPerMinute() >= 0);
        assertEquals(2, c.eventsNear(com.laglens.metrics.Clock.milliTime(), 60_000L));
        c.reset();
        assertFalse(c.available());
    }

    @Test
    void networkTracksPingAndJitter() {
        NetworkCollector n = new NetworkCollector();
        assertFalse(n.available());
        assertFalse(n.packetInspectionAvailable());
        n.recordPing(50);
        n.recordPing(60);
        n.recordPing(55);
        assertTrue(n.available());
        assertEquals(55.0, n.currentPingMs(), 1e-9);
        assertEquals(7.5, n.jitterMs(), 1e-9);
        n.recordDisconnect(123L);
        assertFalse(n.available());
        assertEquals(123L, n.lastDisconnectAtMs());
    }

    @Test
    void serverTickLabelsItsSource() {
        ServerTickCollector s = new ServerTickCollector();
        assertFalse(s.available());
        s.publishIntegrated(42.5);
        assertTrue(s.available());
        assertEquals("integrated", s.source());
        s.markUnavailable();
        assertFalse(s.available());
        assertEquals("unavailable", s.source());
    }

    @Test
    void entityCensusRanksTypes() {
        EntityCollector e = new EntityCollector();
        e.publish(1L, 10, 1, Map.of("minecraft:zombie", 7, "minecraft:cow", 2));
        assertTrue(e.census().available());
        assertEquals("minecraft:zombie", e.topTypes(1).get(0).getKey());
        e.publishBlockEntities(1L, 5, Map.of("minecraft:chest", 5), true);
        assertEquals(5, e.blockEntities().total());
        e.markUnavailable();
        assertFalse(e.census().available());
        assertFalse(e.blockEntities().available());
    }

    @Test
    void modInventorySortsById() {
        ModInventoryCollector m = new ModInventoryCollector();
        m.publishMods(List.of(
                new ModInventoryCollector.ModEntry("zebra", "1", "Z"),
                new ModInventoryCollector.ModEntry("apple", "2", "A")));
        assertEquals("apple", m.mods().get(0).id());
    }

    @Test
    void integrationsDistinguishPresentFromAbsent() {
        var statuses = IntegrationRegistry.check(new IntegrationRegistry.ModQuery() {
            @Override
            public boolean isModLoaded(String id) {
                return "sodium".equals(id);
            }

            @Override
            public String versionOf(String id) {
                return "9.9";
            }
        });
        var sodium = statuses.stream().filter(s -> s.id().equals("sodium")).findFirst()
                .orElseThrow();
        var iris = statuses.stream().filter(s -> s.id().equals("iris")).findFirst().orElseThrow();
        assertTrue(sodium.active());
        assertFalse(iris.active());
        assertTrue(statuses.stream().anyMatch(s -> s.id().equals("laglens-companion")));
    }
}
