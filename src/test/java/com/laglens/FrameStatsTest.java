package com.laglens;

import com.laglens.metrics.FrameStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameStatsTest {
    @Test
    void emptyInputIsNotData() {
        assertTrue(Double.isNaN(FrameStats.average(new double[]{})));
        assertTrue(Double.isNaN(FrameStats.percentile(new double[]{}, 95)));
        assertTrue(Double.isNaN(FrameStats.min(null)));
        assertTrue(Double.isNaN(FrameStats.max(new double[]{})));
    }

    @Test
    void averageIsArithmeticMean() {
        assertEquals(20.0, FrameStats.average(new double[]{10.0, 20.0, 30.0}), 1e-9);
    }

    @Test
    void percentilesInterpolate() {
        double[] samples = {10.0, 20.0, 30.0, 40.0};
        assertEquals(10.0, FrameStats.percentile(samples, 0), 1e-9);
        assertEquals(40.0, FrameStats.percentile(samples, 100), 1e-9);
        assertEquals(25.0, FrameStats.percentile(samples, 50), 1e-9);
        assertEquals(17.5, FrameStats.percentile(samples, 25), 1e-9);
    }

    @Test
    void singleSamplePercentileIsTheSample() {
        assertEquals(33.3, FrameStats.percentile(new double[]{33.3}, 95), 1e-9);
    }

    @Test
    void percentileRejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> FrameStats.percentile(new double[]{1.0}, 101));
    }

    @Test
    void frameMsToFpsConverts() {
        assertEquals(60.0, FrameStats.frameMsToFps(1000.0 / 60.0), 0.05);
        assertTrue(Double.isNaN(FrameStats.frameMsToFps(0.0)));
        assertTrue(Double.isNaN(FrameStats.frameMsToFps(Double.NaN)));
    }
}
