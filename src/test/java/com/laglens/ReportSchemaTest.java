package com.laglens;

import com.google.gson.JsonObject;
import com.laglens.analysis.Finding;
import com.laglens.analysis.Severity;
import com.laglens.capture.CaptureSession;
import com.laglens.capture.ReportSchema;
import com.laglens.capture.ReportWriter;
import com.laglens.collectors.ModInventoryCollector;
import com.laglens.config.LagLensConfig;
import com.laglens.metrics.MetricsSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportSchemaTest {
    private final ReportWriter writer = new ReportWriter();

    @AfterEach
    void shutdown() {
        writer.shutdown();
    }

    private CaptureSession.CaptureResult capture() {
        CaptureSession c = new CaptureSession();
        c.start(CaptureSession.Mode.LIGHT, 0L, 0L);
        MetricsSnapshot s = new MetricsSnapshot.Builder(1_000L, 60_000L)
                .frames(60.0, 16.7, 16.0, 18.0, 25.0, 40.0, 1, 100)
                .memory(512L, 1024L, 4096L, 2L, 30.0)
                .build();
        c.record(s);
        Finding f = new Finding.Builder("Spikes detected, cause not yet isolated")
                .severity(Severity.WARN).nextStep("Capture.").windowMs(60_000L).build();
        return c.stop(List.of(f));
    }

    private ReportWriter.EnvironmentInfo env() {
        return new ReportWriter.EnvironmentInfo("1.21.1", "0.16.14", "0.1.0", "21",
                "TestOS", "4 cores", null, "4096 MB max heap", "player", "host");
    }

    @Test
    void builtReportValidatesAgainstSchema() {
        JsonObject report = writer.buildJson(new LagLensConfig(), capture(), () -> env(),
                List.of(new ModInventoryCollector.ModEntry("sodium", "1.0", "Sodium")),
                List.of("limitation one"));
        assertTrue(ReportSchema.isValid(report));
        assertEquals(ReportSchema.SCHEMA_VERSION, report.get("schema").getAsString());
    }

    @Test
    void missingDataIsUnavailableNeverZero() {
        // No memory/entities/network/server sections populated.
        CaptureSession c = new CaptureSession();
        c.start(CaptureSession.Mode.LIGHT, 0L, 0L);
        c.record(MetricsSnapshot.unavailable(1_000L));
        JsonObject report = writer.buildJson(new LagLensConfig(), c.stop(List.of()), () -> env(),
                List.of(), List.of());
        assertTrue(ReportSchema.isValid(report));
        assertEquals("unavailable", report.getAsJsonObject("memory").get("status").getAsString());
        assertEquals("unavailable",
                report.getAsJsonObject("entities").get("status").getAsString());
        assertFalse(report.toString().contains("\"pingMs\":0"));
    }

    @Test
    void privacyStripOmitsHostAndUser() {
        LagLensConfig cfg = new LagLensConfig();
        cfg.privacyStripEnv = true;
        JsonObject report = writer.buildJson(cfg, capture(), () -> env(), List.of(), List.of());
        JsonObject environment = report.getAsJsonObject("environment");
        assertFalse(environment.has("host"));
        assertFalse(environment.has("user"));
    }

    @Test
    void schemaRejectsWrongVersionAndMissingKeys() {
        JsonObject wrong = new JsonObject();
        wrong.addProperty("schema", "laglens-report/999");
        assertFalse(ReportSchema.isValid(wrong));
        assertFalse(ReportSchema.isValid(null));
    }

    @Test
    void sameInstantExportsDoNotOverwrite(@TempDir Path gameDir) throws Exception {
        CaptureSession.CaptureResult r = capture();
        ReportWriter.ExportResult first = writer
                .exportAsync(gameDir, new LagLensConfig(), r, () -> env(), List.of(), List.of())
                .get(30, TimeUnit.SECONDS);
        ReportWriter.ExportResult second = writer
                .exportAsync(gameDir, new LagLensConfig(), r, () -> env(), List.of(), List.of())
                .get(30, TimeUnit.SECONDS);
        assertTrue(first.ok());
        assertTrue(second.ok());
        assertNotEquals(first.json(), second.json());
    }
}
