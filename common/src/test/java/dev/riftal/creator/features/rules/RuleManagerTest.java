package dev.riftal.creator.features.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.rules.preset.RulePreset;
import dev.riftal.creator.features.rules.preset.RulePresetPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The decision {@code RuleManager.applyPreset} makes - which rules go off, which come on - without
 * a server.
 *
 * <p>{@code /rule preset chaos} is the headline command of the feature and had no test at any
 * level; the engine half needs a live world (see the {@code presetApplyAndClearDriveTheEngine}
 * GameTest), but the set arithmetic is pure and belongs here.
 */
class RuleManagerTest {

    private static RulePreset preset(boolean replace, String... rules) {
        return new RulePreset("test", List.of(rules), replace);
    }

    @Test
    void presetReplaceSemanticsSwitchOffWhatItDoesNotName() {
        RulePresetPlan plan = RulePresetPlan.of(List.of("a", "b"), preset(true, "b", "c"));

        assertEquals(List.of("a"), plan.disable(), "a is active and not named, so it goes off");
        assertEquals(List.of("c"), plan.enable(), "only c is missing");
        assertFalse(plan.isEmpty());
    }

    @Test
    void presetWithoutReplaceOnlyAdds() {
        RulePresetPlan plan = RulePresetPlan.of(List.of("a", "b"), preset(false, "b", "c"));

        assertTrue(plan.disable().isEmpty(), "replace=false must never switch anything off");
        assertEquals(List.of("c"), plan.enable());
    }

    @Test
    void enableIsIdempotent() {
        RulePresetPlan plan = RulePresetPlan.of(List.of("a", "b"), preset(true, "a", "b"));

        assertTrue(plan.enable().isEmpty(), "an already-active rule must not be enabled twice");
        assertTrue(plan.disable().isEmpty());
        assertTrue(plan.isEmpty(), "re-applying the preset that is already on changes nothing");
    }

    @Test
    void planKeepsThePresetsOwnOrder() {
        RulePresetPlan plan = RulePresetPlan.of(List.of("z", "y"), preset(true, "c", "a", "b"));

        assertEquals(List.of("z", "y"), plan.disable(), "disable follows the active order");
        assertEquals(List.of("c", "a", "b"), plan.enable(), "enable follows the preset file order");
    }

    @Test
    void anEmptyReplacePresetIsAClear() {
        RulePresetPlan plan = RulePresetPlan.of(List.of("a", "b"), preset(true));

        assertEquals(List.of("a", "b"), plan.disable());
        assertTrue(plan.enable().isEmpty());
    }
}
