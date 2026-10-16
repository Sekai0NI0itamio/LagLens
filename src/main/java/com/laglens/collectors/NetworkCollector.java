package com.laglens.collectors;

import com.laglens.metrics.RingBuffer;

/**
 * Client-observable network metrics.
 *
 * <p>Genuinely measurable from the client: round-trip latency to the current
 * server (player-list entry) and its variability, plus connect/disconnect
 * events. NOT measurable from here and therefore never reported: packet loss
 * (application-level observation is not network-layer telemetry), the
 * server's internal packet-processing cost, and packet contents (LagLens
 * records no payloads, only counts when a safe hook exists — currently none,
 * so packet inspection is reported as unavailable).
 */
public final class NetworkCollector {
    private final RingBuffer pingHistory;
    private boolean available;
    private long lastDisconnectAtMs = -1L;
    private int disconnects;
    /** Packet inspection is not instrumented in this release. */
    private boolean packetInspectionAvailable;

    public NetworkCollector() {
        this.pingHistory = new RingBuffer(600);
        this.packetInspectionAvailable = false;
    }

    public synchronized void recordPing(int pingMs) {
        if (pingMs >= 0) {
            pingHistory.add(pingMs);
            available = true;
        }
    }

    public synchronized void recordDisconnect(long nowMs) {
        lastDisconnectAtMs = nowMs;
        disconnects++;
        available = false;
    }

    public synchronized boolean available() {
        return available && pingHistory.size() > 0;
    }

    public synchronized double currentPingMs() {
        double[] samples = pingHistory.toArray();
        if (samples.length == 0) {
            return Double.NaN;
        }
        return samples[samples.length - 1];
    }

    /** Mean absolute step-to-step variation over recent samples. */
    public synchronized double jitterMs() {
        double[] samples = pingHistory.toArray();
        if (samples.length < 2) {
            return Double.NaN;
        }
        int from = Math.max(0, samples.length - 60);
        double sum = 0.0;
        int n = 0;
        for (int i = from + 1; i < samples.length; i++) {
            sum += Math.abs(samples[i] - samples[i - 1]);
            n++;
        }
        return n == 0 ? Double.NaN : sum / n;
    }

    public synchronized double[] history() {
        return pingHistory.toArray();
    }

    public synchronized long lastDisconnectAtMs() {
        return lastDisconnectAtMs;
    }

    public synchronized int disconnects() {
        return disconnects;
    }

    public synchronized boolean packetInspectionAvailable() {
        return packetInspectionAvailable;
    }

    public synchronized void reset() {
        pingHistory.clear();
        available = false;
    }
}
