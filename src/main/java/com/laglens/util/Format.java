package com.laglens.util;

/** Small human-readable formatters shared by chat feedback, UI and reports. */
public final class Format {
    private Format() {
    }

    public static String ms(double ms) {
        if (!Double.isFinite(ms)) {
            return "n/a";
        }
        return String.format("%.1f ms", ms);
    }

    public static String bytes(long bytes) {
        if (bytes < 0) {
            return "n/a";
        }
        double mb = bytes / (1024.0 * 1024.0);
        if (mb < 1024.0) {
            return String.format("%.0f MB", mb);
        }
        return String.format("%.2f GB", mb / 1024.0);
    }

    public static String duration(long ms) {
        long s = ms / 1000;
        if (s < 60) {
            return s + " s";
        }
        return (s / 60) + " min " + (s % 60) + " s";
    }

    public static String orUnavailable(String v) {
        return v == null || v.isBlank() ? "unavailable" : v;
    }
}
