package com.laglens.collectors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Installed-mod inventory (ids + versions) plus optional-integration status.
 *
 * <p>Honesty contract: this module reports <em>presence and version</em> only.
 * Java does not expose per-mod CPU time, and time spent in a shared Minecraft
 * method is never attributed to a mod merely for being loaded. Findings that
 * mention mods use {@code Possible contributor} at most, unless a real
 * profiler integration supplies {@code Profile-derived} numbers.
 */
public final class ModInventoryCollector {
    /** One installed mod: id, version, and file/name for the UI. */
    public record ModEntry(String id, String version, String name) {
    }

    /** One optional integration and whether it is currently active. */
    public record IntegrationStatus(String id, String description, boolean active, String detail) {
    }

    private volatile List<ModEntry> mods = Collections.emptyList();
    private volatile List<IntegrationStatus> integrations = Collections.emptyList();

    public void publishMods(List<ModEntry> entries) {
        List<ModEntry> copy = new ArrayList<>(entries);
        copy.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));
        mods = Collections.unmodifiableList(copy);
    }

    public void publishIntegrations(List<IntegrationStatus> statuses) {
        integrations = Collections.unmodifiableList(new ArrayList<>(statuses));
    }

    public List<ModEntry> mods() {
        return mods;
    }

    public List<IntegrationStatus> integrations() {
        return integrations;
    }

    public int modCount() {
        return mods.size();
    }
}
