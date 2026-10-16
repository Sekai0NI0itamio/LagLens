package com.laglens.analysis;

/** How urgently the player should pay attention to a finding. */
public enum Severity {
    INFO("Info"),
    WARN("Warning"),
    CRITICAL("Critical");

    private final String label;

    Severity(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
