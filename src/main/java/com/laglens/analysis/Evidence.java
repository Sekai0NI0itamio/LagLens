package com.laglens.analysis;

/**
 * How strongly a finding's attribution is supported by measurement.
 *
 * <p>LagLens never fabricates per-mod CPU timings. When attribution is
 * incomplete the finding must say so with one of the weaker labels.
 */
public enum Evidence {
    /** Measured with a dedicated instrument (frame timer, MXBean, event hook). */
    DIRECT("Directly measured"),
    /** Derived from a sampling-profiler integration (e.g. Spark, when present). */
    PROFILE_DERIVED("Profile-derived"),
    /** Repeated timing alignment between an event and frame spikes. */
    STRONG_CORRELATION("Strong correlation"),
    /** Plausible contributor without timing proof. */
    POSSIBLE_CONTRIBUTOR("Possible contributor"),
    /** Not enough data to attribute; more capture needed. */
    INSUFFICIENT_EVIDENCE("Insufficient evidence");

    private final String label;

    Evidence(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
