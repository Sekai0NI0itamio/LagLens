package com.laglens.analysis;

/** How much the engine trusts a finding, given sample size and missing data. */
public enum Confidence {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    private final String label;

    Confidence(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
