package com.laglens;

import com.laglens.metrics.SpikeDetector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpikeDetectorTest {
    @Test
    void countAboveIsStrict() {
        assertEquals(1, SpikeDetector.countAbove(new double[]{16.0, 34.0, 34.1}, 34.0));
        assertEquals(0, SpikeDetector.countAbove(null, 34.0));
    }

    @Test
    void emptyWindowIsNone() {
        assertEquals(SpikeDetector.SpikeSeverity.NONE,
                SpikeDetector.classify(new double[]{}, 34.0, 100.0, 3));
    }

    @Test
    void singleSpikeIsIsolated() {
        assertEquals(SpikeDetector.SpikeSeverity.ISOLATED,
                SpikeDetector.classify(new double[]{16.0, 16.5, 40.0}, 34.0, 100.0, 3));
    }

    @Test
    void repeatedSpikesAreRecurring() {
        assertEquals(SpikeDetector.SpikeSeverity.RECURRING,
                SpikeDetector.classify(new double[]{40.0, 16.0, 45.0, 50.0}, 34.0, 100.0, 3));
    }

    @Test
    void anySevereSpikeIsSevere() {
        assertEquals(SpikeDetector.SpikeSeverity.SEVERE,
                SpikeDetector.classify(new double[]{16.0, 120.0}, 34.0, 100.0, 3));
    }

    @Test
    void spikesPerMinuteScales() {
        assertEquals(12.0, SpikeDetector.spikesPerMinute(6, 30.0), 1e-9);
        assertTrue(Double.isNaN(SpikeDetector.spikesPerMinute(6, 0.0)));
    }
}
