package dev.riftal.creator.features.evolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.evolve.stage.StageMath;
import dev.riftal.creator.features.evolve.stage.Stages;
import org.junit.jupiter.api.Test;

/** The evolution ladder's arithmetic. Pure logic, no game bootstrap needed. */
class StageMathTest {

    @Test
    void stageForXpFollowsTheThresholdTable() {
        assertEquals(1, StageMath.stageForXp(0));
        assertEquals(1, StageMath.stageForXp(99));
        assertEquals(2, StageMath.stageForXp(100));
        assertEquals(2, StageMath.stageForXp(299));
        assertEquals(3, StageMath.stageForXp(300));
        assertEquals(3, StageMath.stageForXp(699));
        assertEquals(4, StageMath.stageForXp(700));
        assertEquals(4, StageMath.stageForXp(1499));
        assertEquals(5, StageMath.stageForXp(1500));
        assertEquals(5, StageMath.stageForXp(99_999));
    }

    @Test
    void negativeXpStillReadsAsHatchling() {
        assertEquals(1, StageMath.stageForXp(-1));
        assertEquals(1, StageMath.stageForXp(Integer.MIN_VALUE));
    }

    @Test
    void progressIsAFractionOfTheCurrentBand() {
        assertEquals(0.0F, StageMath.progressFraction(0, 1), 1.0E-6F);
        assertEquals(0.5F, StageMath.progressFraction(50, 1), 1.0E-6F);
        assertEquals(0.0F, StageMath.progressFraction(100, 2), 1.0E-6F);
        assertEquals(0.5F, StageMath.progressFraction(200, 2), 1.0E-6F);
        assertEquals(1.0F, StageMath.progressFraction(1500, 5), 1.0E-6F);
    }

    @Test
    void progressNeverLeavesZeroToOne() {
        for (int stage = Stages.MIN; stage <= Stages.MAX; stage++) {
            for (int xp : new int[] {-500, 0, 1, 99, 100, 699, 1500, 50_000}) {
                float progress = StageMath.progressFraction(xp, stage);
                assertTrue(progress >= 0.0F && progress <= 1.0F,
                        "progress out of range for xp=" + xp + " stage=" + stage + ": " + progress);
            }
        }
    }

    @Test
    void xpToNextCountsDownAndStopsAtApex() {
        assertEquals(100, StageMath.xpToNextStage(0, 1));
        assertEquals(1, StageMath.xpToNextStage(99, 1));
        assertEquals(0, StageMath.xpToNextStage(100, 1));
        assertEquals(288, StageMath.xpToNextStage(412, 3));
        assertEquals(0, StageMath.xpToNextStage(0, 5));
    }

    @Test
    void killXpDependsOnWhatDied() {
        // zombie: hostile, 20 max health
        assertEquals(15, StageMath.xpForKill(false, false, true, 20.0F));
        // ravager: hostile, 100 max health -> tough bonus
        assertEquals(20, StageMath.xpForKill(false, false, true, 100.0F));
        // cow: neither
        assertEquals(3, StageMath.xpForKill(false, false, false, 10.0F));
        // another player
        assertEquals(50, StageMath.xpForKill(true, false, false, 20.0F));
        // wither: boss beats everything else
        assertEquals(200, StageMath.xpForKill(false, true, true, 300.0F));
    }

    @Test
    void toughBonusTriggersExactlyAtTheThreshold() {
        assertEquals(15, StageMath.xpForKill(false, false, true, StageMath.TOUGH_HEALTH - 0.01F));
        assertEquals(20, StageMath.xpForKill(false, false, true, StageMath.TOUGH_HEALTH));
    }

    @Test
    void foodXpIsTwicePerNutrition() {
        assertEquals(0, StageMath.xpForFood(0));
        assertEquals(10, StageMath.xpForFood(5));
        assertEquals(8, StageMath.xpForFood(4));
        assertEquals(0, StageMath.xpForFood(-3));
    }
}
