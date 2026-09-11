package dev.riftal.creator.features.colossus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The phase 3 wall of fire. The plan's on-camera beat is "radius 20 closes toward 6 over 40
 * seconds", and that is exactly what {@link ArenaRing#radiusAt} has to produce.
 */
class ArenaRingTest {

    private static final Vec3 CENTRE = new Vec3(8.0D, 64.0D, 8.0D);

    @Test
    void theRingStartsAtTheArenaRadius() {
        assertEquals(20.0D, ArenaRing.radiusAt(20.0D, 6.0D, 0.35D, 0), 1.0E-9D);
    }

    @Test
    void theRingShrinksLinearlyInBlocksPerSecond() {
        // 400 ticks = 20 s at 0.35 blocks/s = 7 blocks closed.
        assertEquals(13.0D, ArenaRing.radiusAt(20.0D, 6.0D, 0.35D, 400), 1.0E-9D);
        assertEquals(16.5D, ArenaRing.radiusAt(20.0D, 6.0D, 0.35D, 200), 1.0E-9D);
    }

    @Test
    void theWholeCloseTakesFortySeconds() {
        // 20 -> 6 is 14 blocks at 0.35 blocks/s = 40 s = 800 ticks, the number the plan quotes.
        assertEquals(6.0D, ArenaRing.radiusAt(20.0D, 6.0D, 0.35D, 800), 1.0E-9D);
        assertTrue(ArenaRing.radiusAt(20.0D, 6.0D, 0.35D, 799) > 6.0D);
    }

    @Test
    void theRingStopsAtTheMinimumAndNeverInverts() {
        assertEquals(6.0D, ArenaRing.radiusAt(20.0D, 6.0D, 0.35D, 10_000), 1.0E-9D);
        assertEquals(6.0D, ArenaRing.radiusAt(20.0D, 6.0D, 0.35D, Integer.MAX_VALUE), 1.0E-9D);
    }

    @Test
    void negativeTimeIsTreatedAsTheStart() {
        assertEquals(20.0D, ArenaRing.radiusAt(20.0D, 6.0D, 0.35D, -60), 1.0E-9D);
    }

    @Test
    void anArenaSmallerThanTheMinimumStaysItsOwnSize() {
        // /colossus arena set main 6 is legal; the ring must not expand out to 6 from 4, nor
        // clamp to a floor that is larger than the arena itself.
        assertEquals(4.0D, ArenaRing.radiusAt(4.0D, 6.0D, 0.35D, 0), 1.0E-9D);
        assertEquals(4.0D, ArenaRing.radiusAt(4.0D, 6.0D, 0.35D, 5_000), 1.0E-9D);
    }

    @Test
    void outsideIsMeasuredOnTheFloorNotInADome() {
        Vec3 highInside = CENTRE.add(3.0D, 40.0D, 0.0D);
        Vec3 deepInside = CENTRE.add(3.0D, -40.0D, 0.0D);
        assertFalse(ArenaRing.isOutside(CENTRE, highInside, 10.0D));
        assertFalse(ArenaRing.isOutside(CENTRE, deepInside, 10.0D));
    }

    @Test
    void standingExactlyOnTheRingIsStillInside() {
        assertFalse(ArenaRing.isOutside(CENTRE, CENTRE.add(10.0D, 0.0D, 0.0D), 10.0D));
        assertTrue(ArenaRing.isOutside(CENTRE, CENTRE.add(10.001D, 0.0D, 0.0D), 10.0D));
    }

    @Test
    void theRingIsRoundNotSquare() {
        // (7, 7) is 9.9 blocks out - inside a radius of 10 - even though both axes read 7.
        assertFalse(ArenaRing.isOutside(CENTRE, CENTRE.add(7.0D, 0.0D, 7.0D), 10.0D));
        // (8, 8) is 11.3 blocks out and is not.
        assertTrue(ArenaRing.isOutside(CENTRE, CENTRE.add(8.0D, 0.0D, 8.0D), 10.0D));
    }

    @Test
    void aClosingRingEventuallyCatchesSomeoneStandingStill() {
        Vec3 loiterer = CENTRE.add(12.0D, 0.0D, 0.0D);
        assertFalse(ArenaRing.isOutside(CENTRE, loiterer,
                ArenaRing.radiusAt(20.0D, ArenaRing.MIN_RADIUS, ArenaRing.BLOCKS_PER_SECOND, 0)));
        assertTrue(ArenaRing.isOutside(CENTRE, loiterer,
                ArenaRing.radiusAt(20.0D, ArenaRing.MIN_RADIUS, ArenaRing.BLOCKS_PER_SECOND, 600)));
    }

    @Test
    void theTunablesMatchThePlan() {
        assertEquals(6.0D, ArenaRing.MIN_RADIUS, 1.0E-9D);
        assertEquals(0.35D, ArenaRing.BLOCKS_PER_SECOND, 1.0E-9D);
        assertEquals(20, ArenaRing.BURN_INTERVAL_TICKS);
        assertEquals(5, ArenaRing.DRAW_INTERVAL_TICKS);
        assertEquals(48, ArenaRing.RING_POINTS);
    }
}
