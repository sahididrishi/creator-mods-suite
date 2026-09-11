package dev.riftal.creator.features.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.events.lucky.LuckyOutcome;
import dev.riftal.creator.features.events.lucky.LuckyOutcomes;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Plan tests 7 and 8: the weight/luck formula, and a data pack that cannot break the table.
 */
class LuckyOutcomeWeightingTest {

    private static final LuckyOutcome NASTY = outcome("explosion", 10, -2);
    private static final LuckyOutcome PLAIN = outcome("command", 10, 0);
    private static final LuckyOutcome KIND = outcome("items", 10, 2);
    private static final List<LuckyOutcome> TABLE = List.of(NASTY, PLAIN, KIND);

    @Test
    void zeroPlayerLuckCollapsesToThePlainWeights() {
        for (LuckyOutcome outcome : TABLE) {
            assertEquals(outcome.weight(), LuckyOutcomes.weightFor(outcome, 0), 1.0E-9D);
        }
        assertEquals(30.0D, LuckyOutcomes.totalWeight(TABLE, 0), 1.0E-9D);
    }

    @Test
    void luckShiftsWeightByTheDocumentedFormula() {
        double levelIncrease = 1.0D / (1.0D - 2 * LuckyOutcomes.LUCK_STEP / 100.0D);
        assertEquals(10.0D * Math.pow(levelIncrease, 2), LuckyOutcomes.weightFor(KIND, 2), 1.0E-9D);
        assertEquals(10.0D * Math.pow(levelIncrease, -2), LuckyOutcomes.weightFor(NASTY, 2), 1.0E-9D);
        assertEquals(10.0D, LuckyOutcomes.weightFor(PLAIN, 2), 1.0E-9D,
                "a luck-neutral outcome keeps its weight at any player luck");

        assertTrue(LuckyOutcomes.weightFor(KIND, 2) > KIND.weight(), "good outcomes get likelier");
        assertTrue(LuckyOutcomes.weightFor(NASTY, 2) < NASTY.weight(), "bad outcomes get rarer");
        // Negative player luck uses |luck| in the step, so the same outcomes move the same way.
        assertEquals(LuckyOutcomes.weightFor(KIND, 2), LuckyOutcomes.weightFor(KIND, -2), 1.0E-9D);
        assertEquals(1.0316D, levelIncrease * levelIncrease, 1.0E-3D,
                "two luck levels is about a 3% shift, as the plan describes");
    }

    @Test
    void seededRollsMatchTheWeightedShares() {
        Random random = new Random(20260911L);
        Map<String, Integer> hits = new HashMap<>();
        int rolls = 200_000;
        for (int i = 0; i < rolls; i++) {
            LuckyOutcome picked = LuckyOutcomes.pick(TABLE, 2, random.nextDouble());
            assertNotNull(picked);
            hits.merge(picked.type(), 1, Integer::sum);
        }
        double total = LuckyOutcomes.totalWeight(TABLE, 2);
        for (LuckyOutcome outcome : TABLE) {
            double expected = LuckyOutcomes.weightFor(outcome, 2) / total;
            double actual = hits.getOrDefault(outcome.type(), 0) / (double) rolls;
            assertEquals(expected, actual, 0.01D,
                    "share for '" + outcome.type() + "' was " + actual + ", expected " + expected);
        }
    }

    @Test
    void pickCoversTheWholeTableAndNeverFallsOffTheEnd() {
        assertEquals(NASTY, LuckyOutcomes.pick(TABLE, 0, 0.0D));
        assertEquals(KIND, LuckyOutcomes.pick(TABLE, 0, 0.9999999D));
        assertEquals(NASTY, LuckyOutcomes.pick(TABLE, 0, -1.0D), "a roll below 0 is clamped");
        assertEquals(KIND, LuckyOutcomes.pick(TABLE, 0, 4.0D), "a roll above 1 is clamped");
        assertNull(LuckyOutcomes.pick(List.of(), 0, 0.5D), "an empty table picks nothing");
    }

    @Test
    void aBrokenDataPackNeverEmptiesTheTable() {
        assertEquals(LuckyOutcomes.builtIn(), LuckyOutcomes.parse("}{ not json"));
        assertEquals(LuckyOutcomes.builtIn(), LuckyOutcomes.parse("[]"));
        assertEquals(LuckyOutcomes.builtIn(), LuckyOutcomes.parse("{ \"outcomes\": 7 }"));
        assertEquals(LuckyOutcomes.builtIn(),
                LuckyOutcomes.parse("{ \"outcomes\": [ { \"type\": \"nope\" } ] }"),
                "a table of nothing but unknown types falls back to the built-in one");
    }

    @Test
    void parserKeepsTheGoodEntriesAndDropsTheRest() {
        List<LuckyOutcome> parsed = LuckyOutcomes.parse("""
                { "outcomes": [
                    { "type": "nope",      "weight": 5 },
                    { "weight": 5 },
                    { "type": "entity",    "weight": 0,  "id": "minecraft:pig" },
                    { "type": "entity",    "weight": -3, "id": "minecraft:cow" },
                    { "type": "items",     "weight": 7,  "loot_table": "minecraft:chests/igloo_chest", "rolls": 2 },
                    { "type": "effect",    "weight": 2,  "effect": "minecraft:glowing", "duration": 60, "radius": 4.0 },
                    { "type": "explosion", "weight": 1,  "radius": 2.5, "fire": true },
                    { "type": "command",   "weight": 3,  "run": "say hi" }
                ] }
                """);
        assertEquals(4, parsed.size(), "a zero or negative weight is dropped with the unknown types");

        LuckyOutcome items = parsed.get(0);
        assertEquals(LuckyOutcome.TYPE_ITEMS, items.type());
        assertEquals("minecraft:chests/igloo_chest", items.id(), "loot_table lands in id()");
        assertEquals(2, items.count(), "rolls lands in count()");
        assertTrue(items.isKnownType());

        LuckyOutcome effect = parsed.get(1);
        assertEquals("minecraft:glowing", effect.id(), "effect lands in id()");
        assertEquals(60, effect.duration());
        assertEquals(4.0D, effect.radius(), 1.0E-9D);
        assertEquals(0, effect.amplifier(), "a missing amplifier defaults to zero");

        LuckyOutcome explosion = parsed.get(2);
        assertEquals(2.5D, explosion.radius(), 1.0E-9D);
        assertTrue(explosion.fire());

        LuckyOutcome command = parsed.get(3);
        assertEquals("say hi", command.command());
        assertEquals("", command.id());
        assertFalse(command.fire());
    }

    @Test
    void builtInTableIsUsableOnItsOwn() {
        List<LuckyOutcome> builtIn = LuckyOutcomes.builtIn();
        assertFalse(builtIn.isEmpty());
        for (LuckyOutcome outcome : builtIn) {
            assertTrue(outcome.isKnownType(), "built-in outcome '" + outcome.type() + "' is runnable");
            assertTrue(outcome.weight() > 0, "built-in outcomes all have a positive weight");
        }
        assertTrue(LuckyOutcomes.totalWeight(builtIn, 0) > 0.0D);
    }

    private static LuckyOutcome outcome(String type, int weight, int luck) {
        return new LuckyOutcome(type, weight, luck, "", "", 1, 0.0D, 0, 0, false);
    }
}
