package dev.riftal.creator.core.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.riftal.creator.Constants;
import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.core.Feature;
import dev.riftal.creator.platform.Services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code config/creatormods.json} - one on/off switch per feature.
 *
 * <pre>{@code
 * {
 *   "features": { "toolkit": true, "colossus": true, ... }
 * }
 * }</pre>
 *
 * <p>The file is written with every known feature on first run, and unknown keys are dropped on
 * rewrite. A feature that is off registers nothing at all: no blocks, no commands, no HUD, no
 * payloads. That is deliberate - flipping one key and restarting gives a clean recording of exactly
 * one feature.
 */
public final class CreatorConfig {

    private static final String FILE_NAME = Constants.MOD_ID + ".json";
    private static final Map<String, Boolean> FEATURES = new LinkedHashMap<>();
    private static Path path;

    /** Reads (or creates) the config file. Called by {@link CreatorMods#init()} before anything else. */
    public static void load() {
        FEATURES.clear();
        for (Feature feature : CreatorMods.FEATURES) {
            FEATURES.put(feature.id(), Boolean.TRUE);
        }

        path = Services.PLATFORM.getConfigDirectory().resolve(FILE_NAME);
        if (Files.isRegularFile(path)) {
            try {
                JsonObject root = JsonParser
                        .parseString(Files.readString(path, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (root.has("features") && root.get("features").isJsonObject()) {
                    JsonObject features = root.getAsJsonObject("features");
                    for (String id : FEATURES.keySet()) {
                        if (features.has(id)) {
                            FEATURES.put(id, features.get(id).getAsBoolean());
                        }
                    }
                }
            } catch (Exception e) {
                Constants.LOG.error("Could not read {}; every feature stays enabled", path, e);
            }
        }
        save();
    }

    /** True when the feature should run this session. Unknown ids are treated as enabled. */
    public static boolean isEnabled(String featureId) {
        return FEATURES.getOrDefault(featureId, Boolean.TRUE);
    }

    /** Writes a toggle to disk. Takes effect on the next game start. */
    public static void setEnabled(String featureId, boolean enabled) {
        FEATURES.put(featureId, enabled);
        save();
    }

    /** Every toggle, in feature order. */
    public static Map<String, Boolean> toggles() {
        return Map.copyOf(FEATURES);
    }

    /** File name shown in log lines and command output. */
    public static String fileName() {
        return FILE_NAME;
    }

    /** Absolute path of the config file, or null before {@link #load()}. */
    public static Path file() {
        return path;
    }

    private static void save() {
        if (path == null) {
            return;
        }
        JsonObject features = new JsonObject();
        FEATURES.forEach(features::addProperty);
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "Set a feature to false to keep it out of the game entirely.");
        root.add("features", features);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path,
                    new GsonBuilder().setPrettyPrinting().create().toJson(root) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            Constants.LOG.error("Could not write {}", path, e);
        }
    }

    private CreatorConfig() {
    }
}
