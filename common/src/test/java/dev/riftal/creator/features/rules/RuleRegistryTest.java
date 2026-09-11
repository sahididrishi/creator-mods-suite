package dev.riftal.creator.features.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.rules.api.Rule;
import dev.riftal.creator.features.rules.api.RuleContext;
import dev.riftal.creator.features.rules.api.RuleRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code RuleRegistry} is what makes "a new rule is one class" true, and the two properties the
 * rest of the feature leans on are that it keeps <em>registration</em> order (that is the order
 * {@code /rule list} prints and the order hooks fan out in) and that a duplicate id replaces rather
 * than duplicates.
 *
 * <p>The registry is a process-wide static, so every rule here uses an id no shipped rule uses.
 */
class RuleRegistryTest {

    /** A rule that records how often each lifecycle hook was called. No Minecraft types involved. */
    private static final class CountingRule implements Rule {

        private final String id;
        private final int interval;
        int enabled;
        int disabled;
        int ticked;

        CountingRule(String id) {
            this(id, 1);
        }

        CountingRule(String id, int interval) {
            this.id = id;
            this.interval = interval;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public int tickInterval() {
            return interval;
        }

        @Override
        public void onEnable(RuleContext ctx) {
            enabled++;
        }

        @Override
        public void onDisable(RuleContext ctx) {
            disabled++;
        }

        @Override
        public void tick(RuleContext ctx) {
            ticked++;
        }
    }

    @Test
    void rulesComeBackInRegistrationOrderNotAlphabeticalOrder() {
        CountingRule zulu = new CountingRule("test_order_zulu");
        CountingRule alpha = new CountingRule("test_order_alpha");
        RuleRegistry.register(zulu);
        RuleRegistry.register(alpha);

        List<String> ids = RuleRegistry.ids();
        int zuluIndex = ids.indexOf("test_order_zulu");
        int alphaIndex = ids.indexOf("test_order_alpha");

        assertTrue(zuluIndex >= 0 && alphaIndex >= 0, "both rules should be registered");
        assertTrue(zuluIndex < alphaIndex,
                "registration order has to win over alphabetical order, got " + ids);
    }

    @Test
    void lookupIsByIdAndUnknownIdsAreNull() {
        CountingRule rule = new CountingRule("test_lookup");
        RuleRegistry.register(rule);

        assertSame(rule, RuleRegistry.byId("test_lookup"));
        assertTrue(RuleRegistry.contains("test_lookup"));

        assertNull(RuleRegistry.byId("test_does_not_exist"));
        assertFalse(RuleRegistry.contains("test_does_not_exist"));
        assertNull(RuleRegistry.byId(null), "a null id must not blow up command handling");
        assertFalse(RuleRegistry.contains(null));
    }

    @Test
    void reRegisteringAnIdReplacesTheRuleAndKeepsItsPlace() {
        RuleRegistry.register(new CountingRule("test_replace_first"));
        RuleRegistry.register(new CountingRule("test_replace_second"));
        int sizeBefore = RuleRegistry.size();
        int indexBefore = RuleRegistry.ids().indexOf("test_replace_first");

        CountingRule replacement = new CountingRule("test_replace_first");
        RuleRegistry.register(replacement);

        assertEquals(sizeBefore, RuleRegistry.size(), "a duplicate id must not grow the registry");
        assertSame(replacement, RuleRegistry.byId("test_replace_first"));
        assertEquals(indexBefore, RuleRegistry.ids().indexOf("test_replace_first"),
                "replacing a rule must not move it to the end of /rule list");
    }

    @Test
    void theIdListAndRuleViewAreNotWritableByCallers() {
        RuleRegistry.register(new CountingRule("test_immutable"));

        assertThrows(UnsupportedOperationException.class, () -> RuleRegistry.ids().add("nope"));
        assertThrows(UnsupportedOperationException.class, () -> RuleRegistry.all().clear());
    }

    @Test
    void ruleDefaultsAreTheOnesTheEngineAssumes() {
        Rule bare = () -> "test_defaults";

        assertEquals(1, bare.tickInterval(), "an unqualified rule has to tick every tick");
        assertNull(bare.remapBlockDrops(null, null, null, null, null),
                "the default drop remap must leave vanilla alone");
        assertNull(bare.remapMobDrops(null, null),
                "the default mob drop remap must leave vanilla alone");
    }

    @Test
    void countingRuleHooksAreIndependent() {
        CountingRule rule = new CountingRule("test_hooks", 20);

        rule.onEnable(null);
        rule.tick(null);
        rule.tick(null);
        rule.onDisable(null);

        assertEquals(1, rule.enabled);
        assertEquals(2, rule.ticked);
        assertEquals(1, rule.disabled);
        assertEquals(20, rule.tickInterval());
    }
}
