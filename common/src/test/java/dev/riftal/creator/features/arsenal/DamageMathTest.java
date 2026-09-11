package dev.riftal.creator.features.arsenal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.arsenal.mechanic.DamageMath;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The Arsenal damage curves. Pure maths - no registries are touched, so this runs without a
 * bootstrapped game.
 */
class DamageMathTest {

    @Test
    void lightningFallsOffFromTenToFourAcrossThreeBlocks() {
        assertEquals(10.0F, DamageMath.lightningAoe(0.0D), 1.0E-4F);
        assertEquals(7.0F, DamageMath.lightningAoe(1.5D), 1.0E-4F);
        assertEquals(4.0F, DamageMath.lightningAoe(3.0D), 1.0E-4F);
    }

    @Test
    void lightningDoesNothingOutsideItsRadius() {
        assertEquals(0.0F, DamageMath.lightningAoe(3.1D), 1.0E-4F);
        assertEquals(0.0F, DamageMath.lightningAoe(64.0D), 1.0E-4F);
    }

    @Test
    void lightningIsMonotonicNonIncreasing() {
        float previous = Float.MAX_VALUE;
        for (int step = 0; step <= 40; step++) {
            float current = DamageMath.lightningAoe(step * 0.1D);
            assertTrue(current <= previous, "lightningAoe rose at distance " + (step * 0.1D));
            previous = current;
        }
    }

    @Test
    void slamFallsOffFromSixToThreeAcrossSixBlocks() {
        assertEquals(6.0F, DamageMath.slamDamage(0.0D), 1.0E-4F);
        assertEquals(4.5F, DamageMath.slamDamage(3.0D), 1.0E-4F);
        assertEquals(3.0F, DamageMath.slamDamage(6.0D), 1.0E-4F);
        assertEquals(0.0F, DamageMath.slamDamage(6.5D), 1.0E-4F);
    }

    @Test
    void lifestealIsAQuarterAndNeverNegative() {
        assertEquals(2.0F, DamageMath.lifesteal(8.0F), 1.0E-4F);
        assertEquals(0.0F, DamageMath.lifesteal(0.0F), 1.0E-4F);
        assertEquals(0.0F, DamageMath.lifesteal(-3.0F), 1.0E-4F);
    }

    @Test
    void sweepIsHalfAndNeverNegative() {
        assertEquals(4.0F, DamageMath.sweepDamage(8.0F), 1.0E-4F);
        assertEquals(0.0F, DamageMath.sweepDamage(-1.0F), 1.0E-4F);
    }

    @Test
    void absorptionGrowsByTwoHeartsAndCapsAtSix() {
        assertEquals(4.0F, DamageMath.absorptionAfterKill(0.0F), 1.0E-4F);
        assertEquals(12.0F, DamageMath.absorptionAfterKill(10.0F), 1.0E-4F);
        assertEquals(12.0F, DamageMath.absorptionAfterKill(12.0F), 1.0E-4F);
    }

    @Test
    void grappleVelocityAimsAtTheAnchorAndIsSpeedCapped() {
        Vec3 far = new Vec3(40.0D, 0.0D, 0.0D);
        Vec3 velocity = DamageMath.grappleVelocity(far, DamageMath.GRAPPLE_PULL_TICKS);

        assertEquals(DamageMath.GRAPPLE_MAX_SPEED, velocity.x, 1.0E-6D);
        assertEquals(DamageMath.GRAPPLE_LIFT, velocity.y, 1.0E-6D);
        assertEquals(0.0D, velocity.z, 1.0E-6D);
    }

    @Test
    void grappleVelocityArrivesInTheRemainingTicksWhenItCan() {
        Vec3 near = new Vec3(0.0D, 0.0D, 5.0D);
        Vec3 velocity = DamageMath.grappleVelocity(near, 10);

        assertEquals(0.5D, velocity.z, 1.0E-6D);
        assertTrue(velocity.z * 10.0D >= near.z - 1.0E-6D, "should cover the distance in 10 ticks");
    }

    @Test
    void grappleVelocityOnTopOfTheAnchorJustLifts() {
        Vec3 velocity = DamageMath.grappleVelocity(Vec3.ZERO, 4);

        assertEquals(0.0D, velocity.x, 1.0E-6D);
        assertEquals(DamageMath.GRAPPLE_LIFT, velocity.y, 1.0E-6D);
        assertEquals(0.0D, velocity.z, 1.0E-6D);
    }
}
