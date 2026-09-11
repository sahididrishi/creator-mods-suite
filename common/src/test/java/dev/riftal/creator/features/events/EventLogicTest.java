package dev.riftal.creator.features.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.events.lucky.LuckyOutcome;
import dev.riftal.creator.features.events.lucky.LuckyOutcomes;
import dev.riftal.creator.features.events.siege.SiegeWave;
import dev.riftal.creator.features.events.siege.SiegeWaves;
import dev.riftal.creator.features.events.util.CraterShape;
import dev.riftal.creator.features.events.util.EventOptions;
import dev.riftal.creator.features.events.util.SpawnRing;
import dev.riftal.creator.features.events.util.VoidPlane;
import org.junit.jupiter.api.Test;

import java.util.List;

/** Pure-logic coverage for the parts of the director that need no live level. */
class EventLogicTest {

    @Test
    void optionsParseKeyValuePairs() {
        EventOptions options = EventOptions.parse("maxY=60 speed=0.1 bossbar=false waves=3");
        assertEquals(60.0D, options.getDouble("maxY", 0.0D));
        assertEquals(0.1D, options.getDouble("speed", 0.0D));
        assertFalse(options.getBoolean("bossbar", true));
        assertEquals(3, options.getInt("waves", 5));
        assertEquals(9, options.getInt("missing", 9));
        assertEquals("maxY=60 speed=0.1 bossbar=false waves=3", options.raw());
    }

    @Test
    void optionsIgnoreGarbageAndClamp() {
        EventOptions options = EventOptions.parse("speed=fast radius=999");
        assertEquals(0.05D, options.getDouble("speed", 0.05D));
        assertEquals(48.0D, options.getDouble("radius", 12.0D, 2.0D, 48.0D));
        assertTrue(EventOptions.parse("").isEmpty());
    }

    @Test
    void craterIsRoughlySpherical() {
        int count = CraterShape.insideCount(5);
        // 4/3 * pi * 5^3 = 523.6 continuous; the integer lattice lands a little under that.
        assertTrue(count > 480 && count < 560, "r=5 crater had " + count + " cells");
        assertTrue(CraterShape.rim(5).size() < count, "the rim is a subset of the interior");
        for (CraterShape.Offset offset : CraterShape.rim(5)) {
            assertTrue(offset.distanceSq() <= 25, "rim cells stay inside the sphere");
            assertTrue(offset.distanceSq() > 16, "rim cells are the outer shell");
        }
    }

    @Test
    void voidPlaneRisesAndClamps() {
        double y = -64.0D;
        for (int tick = 0; tick < 5000; tick++) {
            y = VoidPlane.advance(y, 0.05D, 40.0D);
        }
        assertEquals(40.0D, y, 1.0E-9D);
        assertEquals(2080, VoidPlane.ticksRemaining(-64.0D, 40.0D, 0.05D));
    }

    @Test
    void timerRecomputesTheRiseSpeed() {
        double speed = VoidPlane.speedForTicks(-64.0D, 40.0D, 1200);
        double y = -64.0D;
        for (int tick = 0; tick < 1200; tick++) {
            y = VoidPlane.advance(y, speed, 40.0D);
        }
        assertEquals(40.0D, y, 0.5D);
        assertEquals(0.5F, VoidPlane.progress(-64.0D, -12.0D, 40.0D), 0.01F);
    }

    @Test
    void spawnRingIsAreaUniform() {
        assertEquals(24.0D, SpawnRing.radiusFor(0.0D, 24.0D, 40.0D), 1.0E-9D);
        assertEquals(40.0D, SpawnRing.radiusFor(1.0D, 24.0D, 40.0D), 1.0E-9D);
        double mid = SpawnRing.radiusFor(0.5D, 24.0D, 40.0D);
        assertTrue(mid > 32.0D && mid < 33.0D,
                "area-uniform midpoint should sit past the arithmetic mean, was " + mid);
    }

    @Test
    void siegeWavesScaleWithDifficulty() {
        assertEquals(11, SiegeWaves.scale(8, SiegeWaves.HARD_FACTOR));
        assertEquals(5, SiegeWaves.scale(8, SiegeWaves.EASY_FACTOR));
        assertEquals(8, SiegeWaves.scale(8, SiegeWaves.NORMAL_FACTOR));
        assertEquals(1, SiegeWaves.scale(1, SiegeWaves.EASY_FACTOR), "a wave never scales to zero");
        assertEquals(SiegeWaves.HARD_FACTOR, SiegeWaves.factorForDifficulty("hard"));
        assertEquals(SiegeWaves.NORMAL_FACTOR, SiegeWaves.factorForDifficulty("nonsense"));
        assertEquals(10, SiegeWaves.defaults().get(0).total(SiegeWaves.NORMAL_FACTOR));
    }

    @Test
    void siegeWaveJsonRoundTrips() {
        List<SiegeWave> waves = SiegeWaves.parse("""
                { "waves": [ { "spawns": [ { "entity": "minecraft:zombie", "count": 4 } ] } ] }
                """);
        assertEquals(1, waves.size());
        assertEquals("minecraft:zombie", waves.get(0).spawns().get(0).entityId());
        assertEquals(4, waves.get(0).spawns().get(0).count());
        assertEquals(SiegeWaves.defaults(), SiegeWaves.parse("not json at all"));
    }

    @Test
    void luckyOutcomesSkipUnknownTypes() {
        List<LuckyOutcome> outcomes = LuckyOutcomes.parse("""
                { "outcomes": [
                    { "type": "nope", "weight": 5 },
                    { "type": "entity", "weight": 3, "id": "minecraft:pig" }
                ] }
                """);
        assertEquals(1, outcomes.size());
        assertEquals("minecraft:pig", outcomes.get(0).id());
    }

    @Test
    void luckyWeightsAreThePlainWeightsAtZeroLuck() {
        List<LuckyOutcome> outcomes = LuckyOutcomes.builtIn();
        double total = 0.0D;
        for (LuckyOutcome outcome : outcomes) {
            assertEquals(outcome.weight(), LuckyOutcomes.weightFor(outcome, 0), 1.0E-9D);
            total += outcome.weight();
        }
        assertEquals(total, LuckyOutcomes.totalWeight(outcomes, 0), 1.0E-9D);
        assertNotNull(LuckyOutcomes.pick(outcomes, 0, 0.0D));
        assertNotNull(LuckyOutcomes.pick(outcomes, 0, 0.999D));
    }

    @Test
    void luckyFormulaFavoursHighLuckOutcomes() {
        LuckyOutcome good = new LuckyOutcome(LuckyOutcome.TYPE_ITEMS, 10, 2,
                "minecraft:chests/simple_dungeon", "", 1, 0.0D, 0, 0, false);
        double expected = 10.0D * Math.pow(1.0D / (1.0D - 2 * LuckyOutcomes.LUCK_STEP / 100.0D), 2);
        assertEquals(expected, LuckyOutcomes.weightFor(good, 2), 1.0E-9D);
        assertTrue(LuckyOutcomes.weightFor(good, 2) > good.weight());
    }

    @Test
    void cumulativePickCoversTheWholeTable() {
        List<LuckyOutcome> outcomes = LuckyOutcomes.builtIn();
        assertEquals(outcomes.get(0), LuckyOutcomes.pick(outcomes, 0, 0.0D));
        assertEquals(outcomes.get(outcomes.size() - 1), LuckyOutcomes.pick(outcomes, 0, 0.9999999D));
    }
}
