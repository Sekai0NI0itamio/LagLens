package com.laglens;

import com.laglens.config.LagLensConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LagLensConfigTest {
    @Test
    void defaultsValidateCleanly() {
        LagLensConfig cfg = new LagLensConfig();
        assertTrue(cfg.validate().isEmpty());
    }

    @Test
    void outOfRangeValuesAreClamped() {
        LagLensConfig cfg = new LagLensConfig();
        cfg.spikeWarnMs = -5.0;
        cfg.historySeconds = 99_999;
        cfg.monitoringLevel = "ULTRA";
        cfg.defaultPage = "nope";
        cfg.reportDir = "../escape";
        assertTrue(cfg.validate().size() >= 4);
        assertEquals(34.0, cfg.spikeWarnMs, 1e-9);
        assertEquals(1800, cfg.historySeconds);
        assertEquals("LIGHT", cfg.monitoringLevel);
        assertEquals("overview", cfg.defaultPage);
        assertEquals("laglens-reports", cfg.reportDir);
    }

    @Test
    void missingFileYieldsDefaults(@TempDir Path dir) {
        LagLensConfig cfg = LagLensConfig.load(dir.resolve("config/laglens.json"));
        assertEquals("overview", cfg.defaultPage);
        assertTrue(Files.isRegularFile(dir.resolve("config/laglens.json")));
    }

    @Test
    void corruptFileFallsBackToDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("laglens.json");
        Files.writeString(file, "{ this is not json");
        LagLensConfig cfg = LagLensConfig.load(file);
        assertEquals(34.0, cfg.spikeWarnMs, 1e-9);
    }

    @Test
    void roundTripPreservesValues(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("laglens.json");
        LagLensConfig cfg = new LagLensConfig();
        cfg.spikeWarnMs = 50.0;
        cfg.autoCaptureOnSevereSpike = true;
        cfg.save(file);
        LagLensConfig back = LagLensConfig.load(file);
        assertEquals(50.0, back.spikeWarnMs, 1e-9);
        assertTrue(back.autoCaptureOnSevereSpike);
    }
}
