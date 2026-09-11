package dev.riftal.creator.features.rules.preset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * A named bundle of rules, loaded from {@code data/<namespace>/rule_presets/<id>.json}:
 *
 * <pre>{@code
 * { "rules": ["random_drops", "crafts_x10"], "replace": true }
 * }</pre>
 *
 * <p>{@code replace: true} (the default) switches off everything that is not in the list, so one
 * command sets up a whole episode. {@code replace: false} only adds.
 *
 * @param id      preset name, i.e. the file name without {@code .json}
 * @param rules   rule ids, in the order they should be switched on
 * @param replace whether rules outside the list are switched off first
 */
public record RulePreset(String id, List<String> rules, boolean replace) {

    public RulePreset {
        rules = List.copyOf(rules);
    }

    /**
     * Parses one preset file. Pure - no registries, no server - so it is unit-testable.
     *
     * @throws com.google.gson.JsonParseException if the file is not a JSON object, or {@code rules}
     *                                            is missing or is not an array of strings
     */
    public static RulePreset fromJson(String id, JsonObject json) {
        JsonArray array = GsonHelper.getAsJsonArray(json, "rules");
        List<String> rules = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            String rule = GsonHelper.convertToString(element, "rules[" + i + "]");
            if (!rules.contains(rule)) {
                rules.add(rule);
            }
        }
        boolean replace = GsonHelper.getAsBoolean(json, "replace", true);
        return new RulePreset(id, rules, replace);
    }

    /** A copy of this preset with every id not in {@code known} dropped. */
    public RulePreset filtered(java.util.Collection<String> known) {
        List<String> kept = new ArrayList<>(rules.size());
        for (String rule : rules) {
            if (known.contains(rule)) {
                kept.add(rule);
            }
        }
        return new RulePreset(id, kept, replace);
    }
}
