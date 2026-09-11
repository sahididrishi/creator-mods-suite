package dev.riftal.creator.features.events.siege;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * The siege wave table: a built-in default plus a parser for
 * {@code data/creator_events/siege_waves/default.json}.
 *
 * <p>Pure logic - no registries, no level - so the scaling maths is unit tested directly.
 */
public final class SiegeWaves {

    /** Easy / Normal / Hard count multipliers, as documented in the plan. */
    public static final double EASY_FACTOR = 0.6D;
    public static final double NORMAL_FACTOR = 1.0D;
    public static final double HARD_FACTOR = 1.4D;

    private static final List<SiegeWave> DEFAULT_WAVES = List.of(
            new SiegeWave(List.of(
                    new SiegeWave.Spawn("minecraft:zombie", 8),
                    new SiegeWave.Spawn("minecraft:skeleton", 2))),
            new SiegeWave(List.of(
                    new SiegeWave.Spawn("minecraft:zombie", 10),
                    new SiegeWave.Spawn("minecraft:pillager", 3))),
            new SiegeWave(List.of(
                    new SiegeWave.Spawn("minecraft:zombie", 8),
                    new SiegeWave.Spawn("minecraft:pillager", 5),
                    new SiegeWave.Spawn("minecraft:vindicator", 2))),
            new SiegeWave(List.of(
                    new SiegeWave.Spawn("minecraft:zombie", 12),
                    new SiegeWave.Spawn("minecraft:pillager", 6),
                    new SiegeWave.Spawn("minecraft:ravager", 1))),
            new SiegeWave(List.of(
                    new SiegeWave.Spawn("minecraft:zombie", 10),
                    new SiegeWave.Spawn("minecraft:pillager", 8),
                    new SiegeWave.Spawn("minecraft:vindicator", 4),
                    new SiegeWave.Spawn("minecraft:ravager", 1),
                    new SiegeWave.Spawn("minecraft:evoker", 1))));

    /** The five hand-written waves used when no data pack overrides them. */
    public static List<SiegeWave> defaults() {
        return DEFAULT_WAVES;
    }

    /**
     * Rounds a base count by the difficulty factor. Never drops a wave to nothing: a wave that
     * wanted mobs always gets at least one.
     */
    public static int scale(int baseCount, double difficultyFactor) {
        if (baseCount <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(baseCount * difficultyFactor));
    }

    /** Difficulty factor by vanilla difficulty name. Unknown names behave as Normal. */
    public static double factorForDifficulty(String difficultyName) {
        if (difficultyName == null) {
            return NORMAL_FACTOR;
        }
        return switch (difficultyName.toLowerCase(java.util.Locale.ROOT)) {
            case "peaceful", "easy" -> EASY_FACTOR;
            case "hard" -> HARD_FACTOR;
            default -> NORMAL_FACTOR;
        };
    }

    /**
     * Parses a wave table.
     *
     * <pre>{@code
     * { "waves": [ { "spawns": [ { "entity": "minecraft:zombie", "count": 8 } ] } ] }
     * }</pre>
     *
     * <p>Malformed entries are skipped rather than thrown: a data pack typo must not brick the
     * event mid-recording. Returns the defaults when nothing usable was found.
     */
    public static List<SiegeWave> parse(String json) {
        List<SiegeWave> waves = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return defaults();
            }
            JsonElement wavesElement = root.getAsJsonObject().get("waves");
            if (wavesElement == null || !wavesElement.isJsonArray()) {
                return defaults();
            }
            for (JsonElement waveElement : wavesElement.getAsJsonArray()) {
                if (!waveElement.isJsonObject()) {
                    continue;
                }
                JsonElement spawnsElement = waveElement.getAsJsonObject().get("spawns");
                if (spawnsElement == null || !spawnsElement.isJsonArray()) {
                    continue;
                }
                List<SiegeWave.Spawn> spawns = new ArrayList<>();
                JsonArray spawnArray = spawnsElement.getAsJsonArray();
                for (JsonElement spawnElement : spawnArray) {
                    if (!spawnElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject spawn = spawnElement.getAsJsonObject();
                    if (!spawn.has("entity")) {
                        continue;
                    }
                    String entity = spawn.get("entity").getAsString();
                    int count = spawn.has("count") ? spawn.get("count").getAsInt() : 1;
                    if (entity.isBlank() || count <= 0) {
                        continue;
                    }
                    spawns.add(new SiegeWave.Spawn(entity, count));
                }
                if (!spawns.isEmpty()) {
                    waves.add(new SiegeWave(List.copyOf(spawns)));
                }
            }
        } catch (RuntimeException ignored) {
            return defaults();
        }
        return waves.isEmpty() ? defaults() : List.copyOf(waves);
    }

    private SiegeWaves() {
    }
}
