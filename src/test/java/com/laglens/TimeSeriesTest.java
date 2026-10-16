package com.laglens;

import com.laglens.metrics.TimeSeries;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeSeriesTest {
    @Test
    void windowsExcludeOldSamples() {
        TimeSeries series = new TimeSeries(10_000L, 100);
        series.add(1_000L, 5.0);
        series.add(9_000L, 7.0);
        assertArrayEquals(new double[]{7.0}, series.presentValues(10_000L, 2_000L));
    }

    @Test
    void missingMarkersAreCountedNotValued() {
        TimeSeries series = new TimeSeries(10_000L, 100);
        series.add(9_000L, null);
        series.add(9_500L, 4.0);
        assertEquals(1, series.missingCount(10_000L, 2_000L));
        assertArrayEquals(new double[]{4.0}, series.presentValues(10_000L, 2_000L));
    }

    @Test
    void horizonEvictsOldSamples() {
        TimeSeries series = new TimeSeries(1_000L, 100);
        series.add(0L, 1.0);
        series.add(5_000L, 2.0);
        assertEquals(1, series.size());
    }

    @Test
    void capacityCapIsHard() {
        TimeSeries series = new TimeSeries(60_000L, 5);
        for (int i = 0; i < 50; i++) {
            series.add(i, (double) i);
        }
        assertEquals(5, series.size());
    }
}
