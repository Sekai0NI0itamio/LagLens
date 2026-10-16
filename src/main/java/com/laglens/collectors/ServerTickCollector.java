package com.laglens.collectors;

/**
 * Server-tick observatory with strict source labeling.
 *
 * <p>A client-only mod cannot see a remote server's internals. Tick data is
 * therefore accepted from exactly two trusted sources:
 * <ul>
 *   <li>{@code integrated} — the singleplayer / LAN integrated server running
 *       in this JVM (average tick time is directly measurable).</li>
 *   <li>{@code companion} — a future consented LagLens server companion
 *       speaking the versioned protocol in {@code docs/SERVER_PROTOCOL.md}.</li>
 * </ul>
 * Anything else (including naive ping-derived "TPS" guesses) is refused: the
 * dashboard shows "unavailable" rather than a fabricated tick time. The 50 ms
 * budget (20 TPS) is used as the reference, with the caveat that an average
 * tick time and sustained TPS are not interchangeable.
 */
public final class ServerTickCollector {
    public static final double TICK_BUDGET_MS = 50.0;

    private double tickMs = Double.NaN;
    private boolean available;
    private String source = "unavailable";

    public synchronized void publishIntegrated(double avgTickMs) {
        if (Double.isFinite(avgTickMs) && avgTickMs >= 0.0) {
            this.tickMs = avgTickMs;
            this.available = true;
            this.source = "integrated";
        }
    }

    public synchronized void publishCompanion(double avgTickMs) {
        if (Double.isFinite(avgTickMs) && avgTickMs >= 0.0) {
            this.tickMs = avgTickMs;
            this.available = true;
            this.source = "companion";
        }
    }

    public synchronized void markUnavailable() {
        this.tickMs = Double.NaN;
        this.available = false;
        this.source = "unavailable";
    }

    public synchronized double tickMs() {
        return tickMs;
    }

    public synchronized boolean available() {
        return available;
    }

    public synchronized String source() {
        return source;
    }
}
