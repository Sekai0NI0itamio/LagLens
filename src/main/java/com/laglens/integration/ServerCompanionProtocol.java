package com.laglens.integration;

/**
 * Versioned protocol design for the optional LagLens server companion.
 *
 * <p>Full specification lives in {@code docs/SERVER_PROTOCOL.md}. Summary:
 * <ul>
 *   <li>Channel {@value #CHANNEL}, protocol version {@value #PROTOCOL_VERSION}.</li>
 *   <li>Server publishes tick-distribution summaries at 1 Hz <em>only</em> to
 *       players with an explicit permission; detailed profiling requires a
 *       separate operator-confirmed session with a bounded duration.</li>
 *   <li>The client never sends commands to the server; the companion never
 *       accepts profiling requests from unauthorized players.</li>
 * </ul>
 * The client-only release works fully without any companion; tick data is
 * then reported as unavailable rather than guessed.
 */
public final class ServerCompanionProtocol {
    public static final int PROTOCOL_VERSION = 1;
    public static final String CHANNEL = "laglens:diagnostics/v1";

    public static final String MSG_TICK_SUMMARY = "tick-summary";
    public static final String MSG_PROFILING_START = "profiling-start";
    public static final String MSG_PROFILING_STOP = "profiling-stop";
    public static final String MSG_PROFILING_DATA = "profiling-data";

    private ServerCompanionProtocol() {
    }
}
