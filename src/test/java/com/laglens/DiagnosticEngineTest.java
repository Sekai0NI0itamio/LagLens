package com.laglens;

import com.laglens.analysis.DiagnosticEngine;
import com.laglens.analysis.Evidence;
import com.laglens.analysis.Finding;
import com.laglens.analysis.Severity;
import com.laglens.config.LagLensConfig;
import com.laglens.metrics.MetricsSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticEngineTest {
    private final LagLensConfig config = new LagLensConfig();

    private MetricsSnapshot.Builder base() {
        // 60 smooth-ish samples with two warn spikes by default; tests adjust per case.
        MetricsSnapshot.Builder b = new MetricsSnapshot.Builder(1_000_000L, 60_000L);
        b.frames(60.0, 16.7, 16.7, 18.0, 40.0, 45.0, 2, 60);
        return b;
    }

    @Test
    void smallSampleSizeDefersJudgment() {
        MetricsSnapshot s = base().frames(20.0, 50.0, 40.0, 90.0, 120.0, 200.0, 10, 5).build();
        List<Finding> findings = DiagnosticEngine.analyze(s, config);
        assertEquals(1, findings.size());
        assertEquals(Evidence.INSUFFICIENT_EVIDENCE, findings.get(0).evidence());
    }

    @Test
    void chunkCoincidenceFiresCaseA() {
        MetricsSnapshot s = base().chunks(400, 60, 5).build();
        List<Finding> findings = DiagnosticEngine.analyze(s, config);
        assertTrue(findings.stream().anyMatch(f -> "terrain".equals(f.page())));
        Finding terrain = findings.stream().filter(f -> "terrain".equals(f.page())).findFirst()
                .orElseThrow();
        assertEquals(Evidence.STRONG_CORRELATION, terrain.evidence());
    }

    @Test
    void gcOverlapFiresCaseB() {
        MetricsSnapshot s = base().memory(1_000_000L, 2_000_000L, 4_000_000L, 4L, 500.0)
                .gcCoincidences(4).build();
        List<Finding> findings = DiagnosticEngine.analyze(s, config);
        assertTrue(findings.stream().anyMatch(f -> "memory".equals(f.page())));
    }

    @Test
    void stableFramesWithHighPingFiresCaseC() {
        MetricsSnapshot s = base().frames(60.0, 16.7, 16.7, 17.0, 17.5, 20.0, 0, 60)
                .network(320.0, 40.0).build();
        List<Finding> findings = DiagnosticEngine.analyze(s, config);
        assertTrue(findings.stream().anyMatch(f -> "network".equals(f.page())));
    }

    @Test
    void badFramesWithoutCoincidenceFiresCaseD() {
        MetricsSnapshot s = base().frames(25.0, 40.0, 38.0, 60.0, 80.0, 120.0, 8, 120).build();
        List<Finding> findings = DiagnosticEngine.analyze(s, config);
        assertTrue(findings.stream().anyMatch(f -> "rendering".equals(f.page())));
    }

    @Test
    void serverTickNeedsAuthoritativeData() {
        // Same bad frames, but WITHOUT server data: no server finding may appear.
        MetricsSnapshot noData = base()
                .frames(10.0, 100.0, 90.0, 150.0, 200.0, 300.0, 20, 120).build();
        assertTrue(DiagnosticEngine.analyze(noData, config).stream()
                .noneMatch(f -> "server".equals(f.page())));

        // With integrated tick data over budget: server finding appears.
        MetricsSnapshot withData = base()
                .frames(10.0, 100.0, 90.0, 150.0, 200.0, 300.0, 20, 120)
                .serverTick(85.0, "integrated").build();
        List<Finding> findings = DiagnosticEngine.analyze(withData, config);
        assertTrue(findings.stream()
                .anyMatch(f -> "server".equals(f.page()) && f.evidence() == Evidence.DIRECT));
    }

    @Test
    void unexplainedSpikesAskForCapture() {
        MetricsSnapshot s = base().build();
        List<Finding> findings = DiagnosticEngine.analyze(s, config);
        assertTrue(findings.stream().anyMatch(f -> "capture".equals(f.page())));
    }

    @Test
    void healthyWindowStaysCalm() {
        MetricsSnapshot s = base().frames(60.0, 16.7, 16.7, 17.0, 17.5, 20.0, 0, 60).build();
        List<Finding> findings = DiagnosticEngine.analyze(s, config);
        assertEquals(1, findings.size());
        assertEquals(Severity.INFO, findings.get(0).severity());
    }

    @Test
    void findingsCarryActionableFields() {
        MetricsSnapshot s = base().chunks(400, 60, 5).build();
        for (Finding f : DiagnosticEngine.analyze(s, config)) {
            assertTrue(f.title() != null && !f.title().isBlank());
            assertTrue(f.nextStep() != null && !f.nextStep().isBlank());
            assertTrue(f.windowMs() > 0);
        }
    }
}
