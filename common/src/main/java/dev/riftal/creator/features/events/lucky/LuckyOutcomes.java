package dev.riftal.creator.features.events.lucky;

import static dev.riftal.creator.Constants.LOG;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * The lucky-rain drop table: parsing, weighting and picking. No Minecraft types, so the weighting
 * maths is unit tested directly; {@link LuckyOutcomeRunner} does the world-facing half.
 */
public final class LuckyOutcomes {

    /** The "luck step" constant of the classic lucky-block weighting. */
    public static final double LUCK_STEP = 0.77D;

    private static final List<LuckyOutcome> BUILT_IN = List.of(
            new LuckyOutcome(LuckyOutcome.TYPE_ITEMS, 10, 1,
                    "minecraft:chests/simple_dungeon", "", 3, 0.0D, 0, 0, false),
            new LuckyOutcome(LuckyOutcome.TYPE_ENTITY, 6, -1,
                    "minecraft:creeper", "", 1, 0.0D, 0, 0, false),
            new LuckyOutcome(LuckyOutcome.TYPE_EXPLOSION, 4, -2,
                    "", "", 1, 3.0D, 0, 0, false),
            new LuckyOutcome(LuckyOutcome.TYPE_COMMAND, 8, 0,
                    "", "summon minecraft:pig ~ ~ ~ {Saddle:1b}", 1, 0.0D, 0, 0, false),
            new LuckyOutcome(LuckyOutcome.TYPE_ENTITY, 5, 2,
                    "minecraft:allay", "", 2, 0.0D, 0, 0, false),
            new LuckyOutcome(LuckyOutcome.TYPE_EFFECT, 5, 0,
                    "minecraft:levitation", "", 1, 0.0D, 100, 0, false));

    /** The table shipped in code, used when the data pack file is missing or unreadable. */
    public static List<LuckyOutcome> builtIn() {
        return BUILT_IN;
    }

    /**
     * Parses {@code data/creator_events/lucky_outcomes/default.json}.
     *
     * <pre>{@code
     * { "outcomes": [ { "type": "items", "weight": 10, "luck": 1,
     *                   "loot_table": "minecraft:chests/simple_dungeon", "rolls": 3 } ] }
     * }</pre>
     *
     * <p>Entries with an unknown {@code type}, a missing {@code type} or a non-positive weight are
     * dropped; the rest still load. Returns the built-in table when nothing usable survives.
     */
    public static List<LuckyOutcome> parse(String json) {
        List<LuckyOutcome> out = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return builtIn();
            }
            JsonElement outcomes = root.getAsJsonObject().get("outcomes");
            if (outcomes == null || !outcomes.isJsonArray()) {
                return builtIn();
            }
            for (JsonElement element : outcomes.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                LuckyOutcome outcome = parseOne(element.getAsJsonObject());
                if (outcome != null) {
                    out.add(outcome);
                }
            }
        } catch (RuntimeException ignored) {
            return builtIn();
        }
        return out.isEmpty() ? builtIn() : List.copyOf(out);
    }

    private static LuckyOutcome parseOne(JsonObject object) {
        if (!object.has("type")) {
            return null;
        }
        String type = object.get("type").getAsString();
        int weight = object.has("weight") ? object.get("weight").getAsInt() : 1;
        if (weight <= 0) {
            return null;
        }
        int luck = object.has("luck") ? object.get("luck").getAsInt() : 0;
        String id = string(object, "id",
                string(object, "loot_table", string(object, "effect", "")));
        String command = string(object, "run", "");
        int count = object.has("count")
                ? object.get("count").getAsInt()
                : (object.has("rolls") ? object.get("rolls").getAsInt() : 1);
        double radius = object.has("radius") ? object.get("radius").getAsDouble() : 0.0D;
        int duration = object.has("duration") ? object.get("duration").getAsInt() : 0;
        int amplifier = object.has("amplifier") ? object.get("amplifier").getAsInt() : 0;
        boolean fire = object.has("fire") && object.get("fire").getAsBoolean();

        LuckyOutcome outcome = new LuckyOutcome(type, weight, luck, id, command,
                Math.max(1, count), radius, duration, amplifier, fire);
        if (!outcome.isKnownType()) {
            // The javadoc has always promised "skipped with a warning" and never emitted one, so a
            // data-pack author whose outcome silently never fired had nothing to go on. `structure`
            // is the common case: the plan lists it in the schema, this feature does not run it.
            LOG.warn("[events] lucky outcome type '{}' is not supported; entry skipped", type);
            return null;
        }
        return outcome;
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    /**
     * The effective weight of one outcome at a given player luck.
     *
     * <p>{@code levelIncrease = 1 / (1 - |luck| * 0.77 / 100)}, then
     * {@code weight * levelIncrease^outcomeLuck}. At {@code playerLuck == 0} this collapses to the
     * plain weight, which is the MVP case.
     */
    public static double weightFor(LuckyOutcome outcome, int playerLuck) {
        if (playerLuck == 0 || outcome.luck() == 0) {
            return outcome.weight();
        }
        double denominator = 1.0D - Math.abs(playerLuck) * LUCK_STEP / 100.0D;
        if (denominator <= 0.0D) {
            return outcome.weight();
        }
        double levelIncrease = 1.0D / denominator;
        return outcome.weight() * Math.pow(levelIncrease, outcome.luck());
    }

    /** Sum of {@link #weightFor} over the table. */
    public static double totalWeight(List<LuckyOutcome> outcomes, int playerLuck) {
        double total = 0.0D;
        for (LuckyOutcome outcome : outcomes) {
            total += weightFor(outcome, playerLuck);
        }
        return total;
    }

    /**
     * Cumulative weighted pick.
     *
     * @param roll 0..1 uniform random
     * @return the chosen outcome, or null when the table is empty
     */
    public static LuckyOutcome pick(List<LuckyOutcome> outcomes, int playerLuck, double roll) {
        if (outcomes.isEmpty()) {
            return null;
        }
        double total = totalWeight(outcomes, playerLuck);
        if (total <= 0.0D) {
            return outcomes.get(0);
        }
        double target = (roll < 0.0D ? 0.0D : Math.min(roll, 0.999999D)) * total;
        double running = 0.0D;
        for (LuckyOutcome outcome : outcomes) {
            running += weightFor(outcome, playerLuck);
            if (target < running) {
                return outcome;
            }
        }
        return outcomes.get(outcomes.size() - 1);
    }

    private LuckyOutcomes() {
    }
}
