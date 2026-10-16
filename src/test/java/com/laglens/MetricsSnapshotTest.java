package com.laglens;

import com.laglens.metrics.MetricsSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsSnapshotTest {
    @Test
    void unavailableMeansUnavailableNotZero() {
        MetricsSnapshot s = MetricsSnapshot.unavailable(123L);
        assertFalse(s.memoryAvailable);
        assertFalse(s.entityAvailable);
        assertFalse(s.networkAvailable);
        assertFalse(s.serverTickAvailable);
        assertTrue(Double.isNaN(s.p95FrameMs));
        assertTrue(Double.isNaN(s.pingMs));
        assertEquals(-1, s.entityCount);
        assertEquals(-1, s.loadedChunks);
        assertEquals("unavailable", s.serverTickSource);
    }

    @Test
    void builderCarriesTypedValues() {
        MetricsSnapshot s = new MetricsSnapshot.Builder(1L, 60_000L)
                .frames(60.0, 16.7, 16.0, 18.0, 25.0, 40.0, 1, 100)
                .memory(512L, 1024L, 4096L, 2L, 30.0)
                .entities(42, 7, true)
                .chunks(225, 12, 1)
                .network(45.0, 5.0)
                .serverTick(32.0, "integrated")
                .gcCoincidences(0)
                .modCount(12)
                .build();
        assertTrue(s.memoryAvailable);
        assertEquals(42, s.entityCount);
        assertEquals(7, s.blockEntityCount);
        assertTrue(s.blockEntityAvailable);
        assertEquals(225, s.loadedChunks);
        assertEquals(45.0, s.pingMs, 1e-9);
        assertEquals("integrated", s.serverTickSource);
        assertEquals(12, s.installedModCount);
    }
}
