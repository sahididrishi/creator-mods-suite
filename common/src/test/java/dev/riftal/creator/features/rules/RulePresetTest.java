package dev.riftal.creator.features.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParseException;
import dev.riftal.creator.features.rules.preset.RulePreset;
import net.minecraft.util.GsonHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

/** Preset parsing: the datapack surface of the feature, so it has to fail loudly and safely. */
class RulePresetTest {

    private static RulePreset parse(String json) {
        return RulePreset.fromJson("test", GsonHelper.parse(json));
    }

    @Test
    void parsesRulesInOrder() {
        RulePreset preset = parse("{\"rules\":[\"random_drops\",\"crafts_x10\"]}");

        assertEquals(List.of("random_drops", "crafts_x10"), preset.rules());
        assertEquals("test", preset.id());
    }

    @Test
    void replaceDefaultsToTrue() {
        assertTrue(parse("{\"rules\":[]}").replace());
        assertTrue(parse("{\"rules\":[],\"replace\":true}").replace());
        assertFalse(parse("{\"rules\":[],\"replace\":false}").replace());
    }

    @Test
    void duplicatesAreCollapsed() {
        RulePreset preset = parse("{\"rules\":[\"one_heart\",\"one_heart\",\"gravity_x3\"]}");

        assertEquals(List.of("one_heart", "gravity_x3"), preset.rules());
    }

    @Test
    void missingRulesArrayIsRejected() {
        assertThrows(JsonParseException.class, () -> parse("{\"replace\":true}"));
    }

    @Test
    void nonStringRuleEntryIsRejected() {
        assertThrows(JsonParseException.class, () -> parse("{\"rules\":[{\"id\":\"nope\"}]}"));
    }

    @Test
    void filteringDropsRulesThisBuildDoesNotKnow() {
        RulePreset preset = parse("{\"rules\":[\"one_heart\",\"does_not_exist\"]}");

        RulePreset filtered = preset.filtered(List.of("one_heart", "gravity_x3"));

        assertEquals(List.of("one_heart"), filtered.rules());
        assertEquals(preset.replace(), filtered.replace());
        assertEquals(preset.id(), filtered.id());
    }

    @Test
    void ruleListIsImmutable() {
        RulePreset preset = parse("{\"rules\":[\"one_heart\"]}");

        assertThrows(UnsupportedOperationException.class, () -> preset.rules().add("gravity_x3"));
    }
}
