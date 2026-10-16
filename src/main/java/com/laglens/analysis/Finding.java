package com.laglens.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One structured diagnostic conclusion.
 *
 * <p>A finding separates what was <em>observed</em> (symptom + evidence with
 * timestamps and windows) from what is still <em>unknown</em>, and always
 * carries a concrete next diagnostic step plus the dashboard page with the
 * supporting detail. Findings never name a mod as the cause unless the
 * evidence is {@link Evidence#DIRECT} or {@link Evidence#PROFILE_DERIVED}.
 */
public final class Finding {
    /** Dashboard page id the finding links to (overview, bottlenecks, terrain, ...). */
    private final String page;
    private final String title;
    private final Severity severity;
    private final Confidence confidence;
    private final Evidence evidence;
    private final String symptom;
    private final List<String> evidenceLines;
    private final long windowMs;
    private final String unknowns;
    private final String nextStep;

    private Finding(Builder b) {
        this.page = b.page;
        this.title = b.title;
        this.severity = b.severity;
        this.confidence = b.confidence;
        this.evidence = b.evidence;
        this.symptom = b.symptom;
        this.evidenceLines = Collections.unmodifiableList(new ArrayList<>(b.evidenceLines));
        this.windowMs = b.windowMs;
        this.unknowns = b.unknowns;
        this.nextStep = b.nextStep;
    }

    public String page() {
        return page;
    }

    public String title() {
        return title;
    }

    public Severity severity() {
        return severity;
    }

    public Confidence confidence() {
        return confidence;
    }

    public Evidence evidence() {
        return evidence;
    }

    public String symptom() {
        return symptom;
    }

    public List<String> evidenceLines() {
        return evidenceLines;
    }

    public long windowMs() {
        return windowMs;
    }

    public String unknowns() {
        return unknowns;
    }

    public String nextStep() {
        return nextStep;
    }

    public static final class Builder {
        private final String title;
        private String page = "overview";
        private Severity severity = Severity.INFO;
        private Confidence confidence = Confidence.LOW;
        private Evidence evidence = Evidence.INSUFFICIENT_EVIDENCE;
        private String symptom = "";
        private final List<String> evidenceLines = new ArrayList<>();
        private long windowMs;
        private String unknowns = "";
        private String nextStep = "";

        public Builder(String title) {
            this.title = title;
        }

        public Builder page(String page) {
            this.page = page;
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder confidence(Confidence confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder evidence(Evidence evidence) {
            this.evidence = evidence;
            return this;
        }

        public Builder symptom(String symptom) {
            this.symptom = symptom;
            return this;
        }

        public Builder addEvidence(String line) {
            this.evidenceLines.add(line);
            return this;
        }

        public Builder windowMs(long windowMs) {
            this.windowMs = windowMs;
            return this;
        }

        public Builder unknowns(String unknowns) {
            this.unknowns = unknowns;
            return this;
        }

        public Builder nextStep(String nextStep) {
            this.nextStep = nextStep;
            return this;
        }

        public Finding build() {
            return new Finding(this);
        }
    }
}
