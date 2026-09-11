package dev.riftal.creator.features.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.events.api.SkyTint;
import org.junit.jupiter.api.Test;

/**
 * The sky tint the server ramps and the client lerps. Plan test 6: a 40-tick ramp to 0.85 is at
 * half strength on tick 20, the fade mirrors it, and nothing ever leaves 0..1.
 */
class SkyTintTest {

    private static final int RAMP_TICKS = 40;

    @Test
    void bloodMoonIsTheRedFromThePlan() {
        assertEquals(0.60F, SkyTint.BLOOD.red(), 1.0E-6F);
        assertEquals(0.00F, SkyTint.BLOOD.green(), 1.0E-6F);
        assertEquals(0.00F, SkyTint.BLOOD.blue(), 1.0E-6F);
        assertEquals(0.85F, SkyTint.BLOOD.strength(), 1.0E-6F);
        assertTrue(SkyTint.BLOOD.isActive());
        assertFalse(SkyTint.NONE.isActive(), "an idle director must not tint anything");
    }

    @Test
    void rampReachesHalfStrengthHalfwayThrough() {
        SkyTint atHalf = rampAt(RAMP_TICKS / 2);
        assertEquals(0.425F, atHalf.strength(), 1.0E-5F);
        assertEquals(0.0F, rampAt(0).strength(), 1.0E-6F);
        assertEquals(SkyTint.BLOOD.strength(), rampAt(RAMP_TICKS).strength(), 1.0E-6F);
    }

    @Test
    void fadeMirrorsTheRampAndEndsAtNothing() {
        assertEquals(SkyTint.BLOOD.strength(), fadeAt(0).strength(), 1.0E-6F);
        assertEquals(0.425F, fadeAt(RAMP_TICKS / 2).strength(), 1.0E-5F);
        assertEquals(0.0F, fadeAt(RAMP_TICKS).strength(), 1.0E-6F);
        assertFalse(fadeAt(RAMP_TICKS).isActive(), "a finished fade leaves the sky alone");
    }

    @Test
    void strengthIsClampedInBothDirections() {
        assertEquals(1.0F, SkyTint.BLOOD.withStrength(4.0F).strength(), 1.0E-6F);
        assertEquals(0.0F, SkyTint.BLOOD.withStrength(-2.0F).strength(), 1.0E-6F);
        for (int tick = -20; tick <= RAMP_TICKS * 3; tick++) {
            float strength = rampAt(tick).strength();
            assertTrue(strength >= 0.0F && strength <= 1.0F, "ramp left 0..1 at tick " + tick);
        }
    }

    @Test
    void mixMovesEachChannelTowardTheTint() {
        SkyTint full = SkyTint.BLOOD.withStrength(1.0F);
        assertEquals(0.60F, full.mix(0, 0.2F), 1.0E-6F, "a full-strength tint replaces the channel");
        assertEquals(0.00F, full.mix(1, 0.7F), 1.0E-6F);
        assertEquals(0.00F, full.mix(2, 0.9F), 1.0E-6F);

        SkyTint half = SkyTint.BLOOD.withStrength(0.5F);
        assertEquals(0.4F, half.mix(0, 0.2F), 1.0E-6F, "halfway between vanilla 0.2 and tint 0.6");

        assertEquals(0.2F, SkyTint.NONE.mix(0, 0.2F), 1.0E-6F, "no tint leaves vanilla untouched");
        assertEquals(0.7F, SkyTint.NONE.mix(1, 0.7F), 1.0E-6F);
        assertEquals(0.9F, SkyTint.NONE.mix(2, 0.9F), 1.0E-6F);
    }

    /** Exactly the expression {@code BloodMoonEvent} uses during its {@code rise} phase. */
    private static SkyTint rampAt(int phaseTick) {
        return SkyTint.BLOOD.withStrength(
                SkyTint.BLOOD.strength() * Math.min(1.0F, (float) phaseTick / RAMP_TICKS));
    }

    /** Exactly the expression {@code BloodMoonEvent} uses during its {@code fade} phase. */
    private static SkyTint fadeAt(int phaseTick) {
        return SkyTint.BLOOD.withStrength(
                SkyTint.BLOOD.strength() * Math.max(0.0F, 1.0F - (float) phaseTick / RAMP_TICKS));
    }
}
