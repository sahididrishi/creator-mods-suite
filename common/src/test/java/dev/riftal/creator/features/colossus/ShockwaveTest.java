package dev.riftal.creator.features.colossus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The slam ring: how fast it travels, who it catches, and which way it throws them.
 *
 * <p>{@code Shockwave.apply} needs a live level, so it is covered by the GameTest
 * {@code slam_damages_dummy_in_ring}. Everything here is the pure half - the band test and the
 * knockback vector - which is where the interesting mistakes live.
 */
class ShockwaveTest {

    private static final Vec3 CENTRE = new Vec3(100.0D, 64.0D, -50.0D);

    private static Vec3 at(double east, double north) {
        return CENTRE.add(east, 0.0D, north);
    }

    @Test
    void theRingStartsAtTheBossAndReachesFullRadiusOnTheLastTick() {
        assertEquals(0.0D, Shockwave.radiusAt(0, 8, 7.0D), 1.0E-9D);
        assertEquals(7.0D, Shockwave.radiusAt(8, 8, 7.0D), 1.0E-9D);
        assertEquals(3.5D, Shockwave.radiusAt(4, 8, 7.0D), 1.0E-9D);
    }

    @Test
    void theRingExpandsLinearly() {
        double previous = -1.0D;
        for (int tick = 0; tick <= Shockwave.EXPANSION_TICKS; tick++) {
            double radius = Shockwave.radiusAt(tick, Shockwave.EXPANSION_TICKS, 7.0D);
            assertTrue(radius > previous, "tick " + tick + " did not advance the ring");
            previous = radius;
        }
    }

    @Test
    void theRingNeverOvershootsItsMaximum() {
        assertEquals(7.0D, Shockwave.radiusAt(99, 8, 7.0D), 1.0E-9D);
        assertEquals(0.0D, Shockwave.radiusAt(-5, 8, 7.0D), 1.0E-9D);
        // A zero-length expansion is the whole ring at once rather than a divide by zero.
        assertEquals(7.0D, Shockwave.radiusAt(0, 0, 7.0D), 1.0E-9D);
    }

    @Test
    void onlyWhatStandsInTheBandIsHit() {
        double inner = 1.5D;
        double outer = 3.0D;
        assertFalse(Shockwave.isInBand(CENTRE, at(0.5D, 0.0D), inner, outer), "0.5 blocks: behind the ring");
        assertTrue(Shockwave.isInBand(CENTRE, at(2.0D, 0.0D), inner, outer), "2.0 blocks: in the band");
        assertFalse(Shockwave.isInBand(CENTRE, at(3.5D, 0.0D), inner, outer), "3.5 blocks: ahead of the ring");
        assertFalse(Shockwave.isInBand(CENTRE, at(8.0D, 0.0D), inner, outer), "8.0 blocks: nowhere near");
    }

    @Test
    void bothEdgesOfTheBandCount() {
        assertTrue(Shockwave.isInBand(CENTRE, at(1.5D, 0.0D), 1.5D, 3.0D));
        assertTrue(Shockwave.isInBand(CENTRE, at(3.0D, 0.0D), 1.5D, 3.0D));
    }

    @Test
    void theBandIgnoresHeightSoAPillarIsNoEscape() {
        Vec3 onAPillar = CENTRE.add(2.0D, 12.0D, 0.0D);
        assertTrue(Shockwave.isInBand(CENTRE, onAPillar, 1.5D, 3.0D));
    }

    @Test
    void theBandIsMeasuredDiagonallyNotPerAxis() {
        // (2, 2) is 2.83 blocks out, so it is inside a [1.5, 3.0] band; (2.5, 2.5) is 3.54 and is not.
        assertTrue(Shockwave.isInBand(CENTRE, at(2.0D, 2.0D), 1.5D, 3.0D));
        assertFalse(Shockwave.isInBand(CENTRE, at(2.5D, 2.5D), 1.5D, 3.0D));
    }

    @Test
    void knockbackPointsStraightAwayFromTheBoss() {
        Vec3 victim = at(3.0D, 4.0D);
        Vec3 impulse = Shockwave.knockback(CENTRE, victim, 1.6D);
        Vec3 outward = victim.subtract(CENTRE);
        assertTrue(impulse.x * outward.x + impulse.z * outward.z > 0.0D, "impulse must point outward");
        // Direction is preserved exactly: (3,4) is a 3-4-5 triangle, so 0.6 / 0.8 of the strength.
        assertEquals(1.6D * 0.6D, impulse.x, 1.0E-9D);
        assertEquals(1.6D * 0.8D, impulse.z, 1.0E-9D);
    }

    @Test
    void knockbackAlwaysLiftsTheVictim() {
        assertEquals(Shockwave.KNOCKBACK_UP, Shockwave.knockback(CENTRE, at(5.0D, 0.0D), 1.6D).y, 1.0E-9D);
        assertEquals(Shockwave.KNOCKBACK_UP, Shockwave.knockback(CENTRE, at(0.0D, -2.0D), 0.1D).y, 1.0E-9D);
    }

    @Test
    void knockbackStrengthIsTheHorizontalLength() {
        Vec3 impulse = Shockwave.knockback(CENTRE, at(-4.0D, 3.0D), 2.5D);
        assertEquals(2.5D, Math.hypot(impulse.x, impulse.z), 1.0E-9D);
    }

    @Test
    void aVictimStandingOnTheBossIsThrownStraightUp() {
        Vec3 impulse = Shockwave.knockback(CENTRE, CENTRE, 1.6D);
        assertEquals(0.0D, impulse.x, 1.0E-9D);
        assertEquals(0.0D, impulse.z, 1.0E-9D);
        assertEquals(Shockwave.KNOCKBACK_UP, impulse.y, 1.0E-9D);
    }

    @Test
    void heightDoesNotLeakIntoTheKnockbackDirection() {
        Vec3 high = CENTRE.add(3.0D, 20.0D, 0.0D);
        Vec3 low = CENTRE.add(3.0D, -20.0D, 0.0D);
        assertEquals(Shockwave.knockback(CENTRE, high, 1.6D), Shockwave.knockback(CENTRE, low, 1.6D));
    }

    @Test
    void theBandIsWideEnoughToCatchAWalkingPlayer() {
        // The ring moves 7 blocks in 8 ticks, so a band narrower than the per-tick step would
        // tunnel straight past people. This is the guard on that.
        double step = 7.0D / Shockwave.EXPANSION_TICKS;
        assertTrue(Shockwave.BAND_WIDTH > step,
                "band " + Shockwave.BAND_WIDTH + " must exceed the per-tick step " + step);
    }
}
