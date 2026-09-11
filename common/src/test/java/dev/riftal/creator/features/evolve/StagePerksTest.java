package dev.riftal.creator.features.evolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.evolve.perk.StagePerk;
import dev.riftal.creator.features.evolve.perk.StagePerks;
import dev.riftal.creator.features.evolve.perk.impl.RoarPerk;
import dev.riftal.creator.features.evolve.perk.impl.StompPerk;
import dev.riftal.creator.features.evolve.stage.EvolutionStage;
import dev.riftal.creator.features.evolve.stage.Stages;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * The perk table: one perk per rung, resolved by the stage's {@code perkKey}, with no stage left
 * silently falling back to Hatchling's Burrow.
 *
 * <p>Nothing here touches a live world - {@code StagePerks.forStage} is a map lookup - so it stays
 * in plain JUnit. The perks' actual effects need a server and live in the GameTests.
 */
class StagePerksTest {

    @Test
    void everyStageResolvesToItsOwnPerk() {
        assertEquals("burrow", StagePerks.forStage(1).key());
        assertEquals("thick_skin", StagePerks.forStage(2).key());
        assertEquals("charge", StagePerks.forStage(3).key());
        assertEquals("stomp", StagePerks.forStage(4).key());
        assertEquals("roar", StagePerks.forStage(5).key());
    }

    @Test
    void thePerkKeyOnEveryStageActuallyExists() {
        for (EvolutionStage stage : Stages.ALL) {
            StagePerk perk = StagePerks.forStage(stage.ordinal());
            assertNotNull(perk, "no perk for stage " + stage.key());
            assertEquals(stage.perkKey(), perk.key(),
                    "stage " + stage.key() + " names a perk key nothing implements");
        }
    }

    @Test
    void thereAreFivePerksAndTheyAreSingletons() {
        assertEquals(Stages.ALL.size(), StagePerks.all().size());

        Set<String> keys = new HashSet<>();
        for (StagePerk perk : StagePerks.all()) {
            assertTrue(keys.add(perk.key()), "duplicate perk key " + perk.key());
        }
        assertSame(StagePerks.forStage(5), StagePerks.forStage(5),
                "perks are shared singletons, not per-call instances");
    }

    @Test
    void outOfRangeStagesClampInsteadOfThrowing() {
        assertEquals(StagePerks.forStage(1).key(), StagePerks.forStage(0).key());
        assertEquals(StagePerks.forStage(1).key(), StagePerks.forStage(-40).key());
        assertEquals(StagePerks.forStage(Stages.MAX).key(), StagePerks.forStage(99).key());
    }

    @Test
    void theTickPeriodDividesThePerkChargeTimes() {
        // The heartbeat only wakes every TICK_PERIOD ticks, so anything a perk counts in ticks has
        // to be a multiple of it or the count never lands on its target.
        assertTrue(StagePerks.TICK_PERIOD > 0, "the heartbeat needs a positive period");
        assertEquals(0, 20 % StagePerks.TICK_PERIOD,
                "Burrow charges for 20 ticks; the heartbeat must divide that");
    }

    @Test
    void stompAndRoarNumbersMatchThePlan() {
        assertEquals(3.0F, StompPerk.MIN_FALL, 1.0E-6F);
        assertEquals(3.0D, StompPerk.RADIUS, 1.0E-6D);
        assertEquals(6.0F, StompPerk.DAMAGE, 1.0E-6F);
        assertEquals(10.0D, RoarPerk.RADIUS, 1.0E-6D);
        assertEquals(100, RoarPerk.WEAKNESS_TICKS);
    }
}
