package com.laglens.integration;

import com.laglens.collectors.ModInventoryCollector;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional-integration registry.
 *
 * <p>Each entry is presence-checked through Fabric Loader; nothing here is a
 * hard dependency. Known rendering/optimization mods are investigation
 * targets: when present we note coexistence, never control. Profiler
 * integrations (Spark) are import points for future profile-derived numbers —
 * until such an import exists, the Mods page states that plainly.
 */
public final class IntegrationRegistry {
    private IntegrationRegistry() {
    }

    /** Known optional integrations and what each one means for LagLens. */
    public static List<ModInventoryCollector.IntegrationStatus> check(ModQuery query) {
        List<ModInventoryCollector.IntegrationStatus> out = new ArrayList<>();
        out.add(entry(query, "sodium",
                "Modern rendering pipeline; frame timings remain comparable, mesh-rebuild hooks differ."));
        out.add(entry(query, "iris",
                "Shader pipeline; GPU-bound diagnosis needs shader-aware caution."));
        out.add(entry(query, "lithium",
                "Server-side style optimizations; client tick sample still valid."));
        out.add(entry(query, "modernfix",
                "Loading-time optimizer; resource-reload timings may shift."));
        out.add(entry(query, "immediatelyfast",
                "HUD/UI batching; HUD-pass frame sampling stays valid."));
        out.add(entry(query, "entityculling",
                "Entity render culling; entity render workload is lower than raw counts suggest."));
        out.add(entry(query, "spark",
                "Sampling profiler; profile-derived per-mod numbers possible when the user runs one."));
        out.add(new ModInventoryCollector.IntegrationStatus("laglens-companion",
                "Optional server companion (tick data).",
                false,
                "Protocol v1 documented in docs/SERVER_PROTOCOL.md; no companion connected."));
        return out;
    }

    private static ModInventoryCollector.IntegrationStatus entry(ModQuery query, String id,
            String description) {
        boolean present = query.isModLoaded(id);
        return new ModInventoryCollector.IntegrationStatus(id, description, present,
                present ? "Detected version " + query.versionOf(id) + "; coexists, not controlled."
                        : "Not installed; LagLens works without it.");
    }

    /** Loader seam so unit tests can simulate mod presence. */
    public interface ModQuery {
        boolean isModLoaded(String id);

        String versionOf(String id);
    }
}
