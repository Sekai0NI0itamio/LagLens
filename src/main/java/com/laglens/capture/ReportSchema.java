package com.laglens.capture;

import java.util.List;

/**
 * Versioned report schema ({@value #SCHEMA_VERSION}).
 *
 * <p>Reports carry their schema version so future LagLens versions can read
 * older captures. Missing measurements are encoded as the string
 * {@code "unavailable"}, never as zero. {@link #requiredTopLevelKeys()} lists
 * the keys {@link #isValid} enforces.
 */
public final class ReportSchema {
    public static final String SCHEMA_VERSION = "laglens-report/1";

    private ReportSchema() {
    }

    public static List<String> requiredTopLevelKeys() {
        return List.of("schema", "generatedAt", "capture", "environment", "frames",
                "memory", "entities", "chunks", "network", "server", "findings",
                "limitations", "mods");
    }

    public static boolean isValid(com.google.gson.JsonObject report) {
        if (report == null) {
            return false;
        }
        if (!report.has("schema") || !SCHEMA_VERSION.equals(safeString(report, "schema"))) {
            return false;
        }
        for (String key : requiredTopLevelKeys()) {
            if (!report.has(key)) {
                return false;
            }
        }
        return report.get("capture").isJsonObject() && report.get("frames").isJsonObject()
                && report.get("findings").isJsonArray();
    }

    private static String safeString(com.google.gson.JsonObject obj, String key) {
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return "";
        }
    }
}
