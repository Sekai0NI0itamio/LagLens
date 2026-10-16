package com.laglens.collectors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Client-side entity census (counts only, no per-entity timings).
 *
 * <p>The Minecraft-facing part (in {@code ClientEntityCensus}) iterates the
 * client world at the sampler cadence and records totals plus per-type counts.
 * This class holds the resulting immutable census so analysis, UI and reports
 * share one consistent view. Continuous per-entity timings are deliberately
 * NOT collected: they are expensive and Java offers no per-mod CPU attribution
 * anyway (see the Mods page for the honest version of that story).
 */
public final class EntityCollector {
    /** One census snapshot: totals plus the top entity types by count. */
    public record Census(long timestampMs, int total, int players, Map<String, Integer> byType,
            boolean available) {
    }

    private volatile Census last = new Census(0L, -1, -1, Collections.emptyMap(), false);
    private volatile BlockEntities blockEntities =
            new BlockEntities(0L, -1, Collections.emptyMap(), false);

    /** Block-entity census from bounded chunk sampling (counts only). */
    public record BlockEntities(long timestampMs, int total, Map<String, Integer> byType,
            boolean available) {
    }

    public void publish(long timestampMs, int total, int players, Map<String, Integer> byType) {
        Map<String, Integer> copy = new TreeMap<>();
        if (byType != null) {
            copy.putAll(byType);
        }
        last = new Census(timestampMs, total, players, Collections.unmodifiableMap(copy), true);
    }

    public void markUnavailable() {
        last = new Census(System.currentTimeMillis(), -1, -1, Collections.emptyMap(), false);
        blockEntities =
                new BlockEntities(System.currentTimeMillis(), -1, Collections.emptyMap(), false);
    }

    public Census census() {
        return last;
    }

    public void publishBlockEntities(long timestampMs, int total, Map<String, Integer> byType,
            boolean detailed) {
        Map<String, Integer> copy = new TreeMap<>();
        if (detailed && byType != null) {
            copy.putAll(byType);
        }
        blockEntities =
                new BlockEntities(timestampMs, total, Collections.unmodifiableMap(copy), true);
    }

    public BlockEntities blockEntities() {
        return blockEntities;
    }

    /** Top N types by count for compact UI/report rendering. */
    public List<Map.Entry<String, Integer>> topTypes(int n) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(last.byType().entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return entries.subList(0, Math.min(n, entries.size()));
    }
}
