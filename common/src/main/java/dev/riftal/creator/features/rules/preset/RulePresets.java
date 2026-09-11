package dev.riftal.creator.features.rules.preset;

import com.google.gson.JsonObject;
import dev.riftal.creator.features.rules.api.RuleRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

import java.io.BufferedReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static dev.riftal.creator.Constants.LOG;

/**
 * Reads presets straight out of the server's data-pack {@link ResourceManager}.
 *
 * <p>There is deliberately no reload listener: the manager is rebuilt by {@code /reload}, so
 * caching against the manager instance gives datapack reloading for free and costs one identity
 * comparison per lookup. {@code /rule reload} drops the cache by hand for the impatient.
 */
public final class RulePresets {

    private static final String DIRECTORY = "rule_presets";
    private static final String SUFFIX = ".json";

    private static ResourceManager cachedFor;
    private static Map<String, RulePreset> cache = Map.of();

    /** Every preset the loaded data packs define, keyed by name, sorted for stable output. */
    public static Map<String, RulePreset> all(MinecraftServer server) {
        if (server == null) {
            return Map.of();
        }
        ResourceManager manager = server.getResourceManager();
        if (manager != cachedFor) {
            cache = read(manager);
            cachedFor = manager;
        }
        return cache;
    }

    /** The preset with this name, or {@code null}. */
    public static RulePreset byId(MinecraftServer server, String id) {
        return all(server).get(id);
    }

    public static List<String> ids(MinecraftServer server) {
        return List.copyOf(all(server).keySet());
    }

    /** Forces the next lookup to re-read the data packs. Returns how many presets are now loaded. */
    public static int reload(MinecraftServer server) {
        cachedFor = null;
        cache = Map.of();
        return all(server).size();
    }

    /** Drops every cached preset. Called when a server shuts down. */
    public static void clear() {
        cachedFor = null;
        cache = Map.of();
    }

    private static Map<String, RulePreset> read(ResourceManager manager) {
        Map<ResourceLocation, Resource> found =
                manager.listResources(DIRECTORY, location -> location.getPath().endsWith(SUFFIX));
        Map<String, RulePreset> parsed = new TreeMap<>();
        int skipped = 0;
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            String path = entry.getKey().getPath();
            String name = path.substring(DIRECTORY.length() + 1, path.length() - SUFFIX.length());
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonObject json = GsonHelper.parse(reader);
                RulePreset preset = RulePreset.fromJson(name, json);
                RulePreset usable = preset.filtered(RuleRegistry.ids());
                if (usable.rules().size() != preset.rules().size()) {
                    LOG.warn("[rules] preset '{}' names {} unknown rule(s); they were skipped",
                            name, preset.rules().size() - usable.rules().size());
                }
                parsed.put(name, usable);
            } catch (Exception e) {
                skipped++;
                LOG.warn("[rules] could not read preset '{}': {}", entry.getKey(), e.toString());
            }
        }
        if (skipped > 0) {
            LOG.warn("[rules] {} preset file(s) were unreadable and are not available", skipped);
        }
        return new LinkedHashMap<>(parsed);
    }

    private RulePresets() {
    }
}
