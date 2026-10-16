package com.laglens.metrics;

import java.util.Arrays;

/**
 * Fixed-capacity ring buffer of double samples.
 *
 * <p>Oldest samples are overwritten once the buffer is full, so memory use is
 * constant regardless of session length. Not thread-safe; callers must
 * synchronize externally (the session only touches collectors on the client
 * thread).
 */
public final class RingBuffer {
    private final double[] data;
    private int start;
    private int size;

    public RingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.data = new double[capacity];
    }

    public void add(double value) {
        if (size < data.length) {
            data[(start + size) % data.length] = value;
            size++;
        } else {
            data[start] = value;
            start = (start + 1) % data.length;
        }
    }

    /** Number of samples currently held. */
    public int size() {
        return size;
    }

    public int capacity() {
        return data.length;
    }

    public boolean isFull() {
        return size == data.length;
    }

    public void clear() {
        start = 0;
        size = 0;
    }

    /** Returns samples oldest-first. Never exposes the internal array. */
    public double[] toArray() {
        double[] out = new double[size];
        for (int i = 0; i < size; i++) {
            out[i] = data[(start + i) % data.length];
        }
        return out;
    }

    /** Returns a sorted copy of the current samples for percentile math. */
    public double[] sortedCopy() {
        double[] out = toArray();
        Arrays.sort(out);
        return out;
    }
}
