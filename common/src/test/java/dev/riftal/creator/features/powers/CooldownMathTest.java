package dev.riftal.creator.features.powers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.powers.data.CooldownMath;
import org.junit.jupiter.api.Test;

/**
 * The pure arithmetic behind the cooldown sweep, the Ground Pound falloff and the Fire Burst cone
 * (plan 03 section 9, unit tests 1-3, 16 and 17).
 */
class CooldownMathTest {

    // ------------------------------------------------------------------ readyAt / remaining

    @Test
    void readyAtIsAnAbsoluteTick() {
        assertEquals(160L, CooldownMath.readyAt(100L, 60));
        assertEquals(100L, CooldownMath.readyAt(100L, 0));
    }

    @Test
    void readyAtNeverGoesBackwards() {
        // A negative cooldown is a bug upstream; it must not make an ability ready in the past,
        // because the HUD would then draw a sweep that is already over.
        assertEquals(100L, CooldownMath.readyAt(100L, -40));
    }

    @Test
    void remainingClampsToZero() {
        assertEquals(30, CooldownMath.remaining(160L, 130L));
        assertEquals(0, CooldownMath.remaining(160L, 160L));
        assertEquals(0, CooldownMath.remaining(160L, 200L));
    }

    @Test
    void remainingSurvivesAnAbsurdReadyTick() {
        // readyAt is a long, remaining is an int: a corrupt attachment must not overflow into a
        // negative "remaining" that reads as ready.
        assertEquals(Integer.MAX_VALUE, CooldownMath.remaining(Long.MAX_VALUE, 0L));
    }

    // ------------------------------------------------------------------ fraction / sweep

    @Test
    void fractionRunsFromZeroToOneAcrossTheWindow() {
        assertEquals(0.0F, CooldownMath.fraction(100L, 160L, 100L), 1.0E-6F);
        assertEquals(0.5F, CooldownMath.fraction(100L, 160L, 130L), 1.0E-6F);
        assertEquals(1.0F, CooldownMath.fraction(100L, 160L, 160L), 1.0E-6F);
    }

    @Test
    void fractionClampsOutsideTheWindow() {
        assertEquals(0.0F, CooldownMath.fraction(100L, 160L, 40L), 1.0E-6F);
        assertEquals(1.0F, CooldownMath.fraction(100L, 160L, 9999L), 1.0E-6F);
    }

    @Test
    void aZeroLengthWindowReadsAsReadyInsteadOfDividingByZero() {
        assertEquals(1.0F, CooldownMath.fraction(100L, 100L, 100L), 1.0E-6F);
        assertEquals(1.0F, CooldownMath.fraction(160L, 100L, 100L), 1.0E-6F);
    }

    @Test
    void sweepRemainingIsTheInverseOfFraction() {
        assertEquals(1.0F, CooldownMath.sweepRemaining(100L, 160L, 100L), 1.0E-6F);
        assertEquals(0.5F, CooldownMath.sweepRemaining(100L, 160L, 130L), 1.0E-6F);
        assertEquals(0.0F, CooldownMath.sweepRemaining(100L, 160L, 160L), 1.0E-6F);
    }

    // ------------------------------------------------------------------ ground pound falloff

    @Test
    void poundFalloffIsFullAtTheCentreAndAQuarterAtTheRim() {
        assertEquals(1.0F, CooldownMath.poundFalloff(0.0D, 6.0D), 1.0E-6F);
        assertEquals(0.5F, CooldownMath.poundFalloff(3.0D, 6.0D), 1.0E-6F);
        assertEquals(0.25F, CooldownMath.poundFalloff(6.0D, 6.0D), 1.0E-6F);
    }

    @Test
    void poundFalloffNeverDropsBelowAQuarterInsideTheRadius() {
        assertEquals(0.25F, CooldownMath.poundFalloff(5.5D, 6.0D), 1.0E-6F);
    }

    @Test
    void poundFalloffIsZeroOutsideTheRadiusAndForANonsenseRadius() {
        assertEquals(0.0F, CooldownMath.poundFalloff(6.01D, 6.0D), 1.0E-6F);
        assertEquals(0.0F, CooldownMath.poundFalloff(1.0D, 0.0D), 1.0E-6F);
    }

    @Test
    void poundFalloffIsMonotonic() {
        float previous = Float.MAX_VALUE;
        for (int step = 0; step <= 60; step++) {
            float value = CooldownMath.poundFalloff(step / 10.0D, 6.0D);
            assertTrue(value <= previous, "falloff must never rise; step " + step);
            previous = value;
        }
        // 8 damage at the epicentre, 2 at the rim - the numbers the plan's balance table promises.
        assertEquals(8.0F, 8.0F * CooldownMath.poundFalloff(0.0D, 6.0D), 1.0E-5F);
        assertEquals(2.0F, 8.0F * CooldownMath.poundFalloff(6.0D, 6.0D), 1.0E-5F);
    }

    // ------------------------------------------------------------------ fire burst cone

    /** Fire Burst uses a 70 degree cone, i.e. 35 degrees either side of the look vector. */
    private static final double COS_35 = Math.cos(Math.toRadians(35.0D));

    @Test
    void straightAheadIsInsideTheCone() {
        assertTrue(CooldownMath.inCone(1, 0, 0, 5, 0, 0, COS_35));
    }

    @Test
    void behindAndSidewaysAreOutsideTheCone() {
        assertFalse(CooldownMath.inCone(1, 0, 0, -5, 0, 0, COS_35));
        assertFalse(CooldownMath.inCone(1, 0, 0, 0, 0, 5, COS_35));
    }

    @Test
    void theConeEdgeIsAtThirtyFiveDegrees() {
        double inside = Math.toRadians(34.0D);
        double outside = Math.toRadians(36.0D);
        assertTrue(CooldownMath.inCone(1, 0, 0,
                Math.cos(inside) * 4.0D, 0, Math.sin(inside) * 4.0D, COS_35));
        assertFalse(CooldownMath.inCone(1, 0, 0,
                Math.cos(outside) * 4.0D, 0, Math.sin(outside) * 4.0D, COS_35));
    }

    @Test
    void aTargetStandingInsideThePlayerCountsAsInTheCone() {
        // Zero-length vectors have no direction; refusing them would make the burst miss something
        // pressed against the creator's face.
        assertTrue(CooldownMath.inCone(1, 0, 0, 0, 0, 0, COS_35));
    }

    @Test
    void theConeIgnoresTargetDistance() {
        assertTrue(CooldownMath.inCone(1, 0, 0, 0.2D, 0, 0, COS_35));
        assertTrue(CooldownMath.inCone(1, 0, 0, 40.0D, 0, 0, COS_35));
    }
}
