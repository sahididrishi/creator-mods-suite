package dev.riftal.creator.features.colossus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.BossEvent;
import org.junit.jupiter.api.Test;

/**
 * The health thresholds the whole fight hangs off. These are the numbers a creator sees on camera
 * when they type {@code /colossus hp 66}, so the boundaries are pinned exactly rather than
 * approximately.
 */
class BossPhaseTest {

    @Test
    void fullHealthIsPhaseOne() {
        assertSame(BossPhase.P1, BossPhase.forHealthFraction(1.0F));
        assertSame(BossPhase.P1, BossPhase.forHealthFraction(0.9F));
    }

    @Test
    void boundaryAtSixtySixIsExclusiveAbove() {
        assertSame(BossPhase.P1, BossPhase.forHealthFraction(0.661F));
        assertSame(BossPhase.P2, BossPhase.forHealthFraction(BossPhase.P2_THRESHOLD));
        assertSame(BossPhase.P2, BossPhase.forHealthFraction(0.659F));
    }

    @Test
    void boundaryAtThirtyThreeIsExclusiveAbove() {
        assertSame(BossPhase.P2, BossPhase.forHealthFraction(0.331F));
        assertSame(BossPhase.P3, BossPhase.forHealthFraction(BossPhase.P3_THRESHOLD));
        assertSame(BossPhase.P3, BossPhase.forHealthFraction(0.0F));
    }

    @Test
    void outOfRangeFractionsClampToTheEnds() {
        assertSame(BossPhase.P1, BossPhase.forHealthFraction(4.0F));
        assertSame(BossPhase.P3, BossPhase.forHealthFraction(-1.0F));
    }

    @Test
    void phasesNeverRunBackwardsDuringAFight() {
        // Healing the boss back to full must not undo the enrage.
        assertSame(BossPhase.P3, BossPhase.next(BossPhase.P3, 1.0F));
        assertSame(BossPhase.P2, BossPhase.next(BossPhase.P2, 0.8F));
        // But damage still advances it.
        assertSame(BossPhase.P2, BossPhase.next(BossPhase.P1, 0.5F));
        assertSame(BossPhase.P3, BossPhase.next(BossPhase.P1, 0.1F));
        assertSame(BossPhase.P1, BossPhase.next(BossPhase.P1, 1.0F));
    }

    @Test
    void indexRoundTripsAndClampsJunk() {
        for (BossPhase phase : BossPhase.values()) {
            assertSame(phase, BossPhase.byIndex(phase.index()), phase.name());
        }
        assertSame(BossPhase.P1, BossPhase.byIndex(0));
        assertSame(BossPhase.P1, BossPhase.byIndex(9));
        assertSame(BossPhase.P1, BossPhase.byIndex(-3));
    }

    @Test
    void barColoursMatchTheRecordedBeats() {
        assertEquals(BossEvent.BossBarColor.YELLOW, BossPhase.P1.barColor());
        assertEquals(BossEvent.BossBarColor.RED, BossPhase.P2.barColor());
        assertEquals(BossEvent.BossBarColor.PURPLE, BossPhase.P3.barColor());
    }

    @Test
    void onlyPhaseThreeIsEnraged() {
        assertFalse(BossPhase.P1.isEnraged());
        assertFalse(BossPhase.P2.isEnraged());
        assertTrue(BossPhase.P3.isEnraged());
    }

    @Test
    void indicesAreOneTwoThree() {
        assertEquals(1, BossPhase.P1.index());
        assertEquals(2, BossPhase.P2.index());
        assertEquals(3, BossPhase.P3.index());
    }
}
