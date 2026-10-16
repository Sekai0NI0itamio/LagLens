package com.laglens;

import com.laglens.capture.CaptureSession;
import com.laglens.metrics.MetricsSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureSessionTest {
    @Test
    void doubleStartIsRefused() {
        CaptureSession c = new CaptureSession();
        assertTrue(c.start(CaptureSession.Mode.LIGHT, 0L, 0L));
        assertFalse(c.start(CaptureSession.Mode.DETAILED, 0L, 0L));
        assertNotNull(c.stop(List.of()));
    }

    @Test
    void idleStopReturnsNull() {
        assertNull(new CaptureSession().stop(List.of()));
    }

    @Test
    void snapshotsAreBoundedAndDropsCounted() {
        CaptureSession c = new CaptureSession();
        c.start(CaptureSession.Mode.DETAILED, 0L, 0L);
        MetricsSnapshot s = MetricsSnapshot.unavailable(0L);
        for (int i = 0; i < CaptureSession.MAX_SNAPSHOTS + 50; i++) {
            c.record(s);
        }
        CaptureSession.CaptureResult r = c.stop(List.of());
        assertNotNull(r);
        assertEquals(CaptureSession.MAX_SNAPSHOTS, r.snapshots().size());
        assertEquals(50, r.droppedSnapshots());
        assertEquals(CaptureSession.Mode.DETAILED, r.mode());
    }

    @Test
    void autoStopFiresAfterDeadline() {
        CaptureSession c = new CaptureSession();
        c.start(CaptureSession.Mode.LIGHT, 10_000L, 5_000L);
        assertFalse(c.checkAutoStop(14_999L));
        assertTrue(c.checkAutoStop(15_000L));
    }

    @Test
    void recordingOnlyWhileActive() {
        CaptureSession c = new CaptureSession();
        c.record(MetricsSnapshot.unavailable(0L));
        assertEquals(0, c.snapshotCount());
    }
}
