package com.laglens;

import com.laglens.metrics.RingBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingBufferTest {
    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new RingBuffer(0));
    }

    @Test
    void keepsInsertionOrderUntilFull() {
        RingBuffer buf = new RingBuffer(4);
        buf.add(1.0);
        buf.add(2.0);
        assertArrayEquals(new double[]{1.0, 2.0}, buf.toArray());
        assertEquals(2, buf.size());
    }

    @Test
    void overwritesOldestWhenFull() {
        RingBuffer buf = new RingBuffer(3);
        buf.add(1.0);
        buf.add(2.0);
        buf.add(3.0);
        buf.add(4.0);
        assertTrue(buf.isFull());
        assertArrayEquals(new double[]{2.0, 3.0, 4.0}, buf.toArray());
    }

    @Test
    void clearResetsSize() {
        RingBuffer buf = new RingBuffer(3);
        buf.add(1.0);
        buf.clear();
        assertEquals(0, buf.size());
        assertArrayEquals(new double[]{}, buf.toArray());
    }

    @Test
    void sortedCopyDoesNotMutateOrder() {
        RingBuffer buf = new RingBuffer(4);
        buf.add(3.0);
        buf.add(1.0);
        buf.add(2.0);
        assertArrayEquals(new double[]{1.0, 2.0, 3.0}, buf.sortedCopy());
        assertArrayEquals(new double[]{3.0, 1.0, 2.0}, buf.toArray());
    }
}
