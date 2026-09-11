package dev.riftal.creator.features.arsenal.mechanic;

import net.minecraft.world.phys.Vec3;

/**
 * Every number the Arsenal weapons deal, in one pure-Java place. No Minecraft state is touched, so
 * this class unit-tests without a bootstrapped game.
 */
public final class DamageMath {

    /** Radius of the Storm Bow lightning blast, in blocks. */
    public static final double LIGHTNING_RADIUS = 3.0D;

    /** Damage at the centre of the lightning blast. */
    public static final float LIGHTNING_CENTRE_DAMAGE = 10.0F;

    /** Damage at the very edge of the lightning blast. */
    public static final float LIGHTNING_EDGE_DAMAGE = 4.0F;

    /** Radius the Gravity Hammer lifts and slams within, in blocks. */
    public static final double SLAM_RADIUS = 6.0D;

    /** Impact damage at the hammer holder's feet. */
    public static final float SLAM_CENTRE_DAMAGE = 6.0F;

    /** Impact damage at the edge of the hammer's radius. */
    public static final float SLAM_EDGE_DAMAGE = 3.0F;

    /** Fraction of the damage dealt that the Soul Scythe returns as health. */
    public static final float LIFESTEAL_RATIO = 0.25F;

    /** Fraction of the damage dealt that the Soul Scythe's own sweep passes to neighbours. */
    public static final float SWEEP_RATIO = 0.5F;

    /** Absorption granted per soul, in half-hearts. */
    public static final float ABSORPTION_PER_SOUL = 4.0F;

    /** Absorption ceiling, in half-hearts (6 golden hearts). */
    public static final float ABSORPTION_CAP = 12.0F;

    /** Ticks the grapple takes to reel the player in. */
    public static final int GRAPPLE_PULL_TICKS = 10;

    /** Hard cap on the grapple's pull speed, in blocks per tick. */
    public static final double GRAPPLE_MAX_SPEED = 1.8D;

    /** Constant upward nudge applied every pull tick so the player clears ledges. */
    public static final double GRAPPLE_LIFT = 0.08D;

    /** How close the player has to get to the anchor before the pull ends. */
    public static final double GRAPPLE_ARRIVAL_DISTANCE = 1.5D;

    /**
     * Lightning blast damage, linear from {@value #LIGHTNING_CENTRE_DAMAGE} at the strike point to
     * {@value #LIGHTNING_EDGE_DAMAGE} at {@value #LIGHTNING_RADIUS} blocks, and 0 beyond.
     */
    public static float lightningAoe(double distance) {
        if (distance > LIGHTNING_RADIUS) {
            return 0.0F;
        }
        double d = Math.max(0.0D, distance);
        double falloff = (LIGHTNING_CENTRE_DAMAGE - LIGHTNING_EDGE_DAMAGE) * (d / LIGHTNING_RADIUS);
        return (float) (LIGHTNING_CENTRE_DAMAGE - falloff);
    }

    /**
     * Hammer slam impact damage, linear from {@value #SLAM_CENTRE_DAMAGE} at the player to
     * {@value #SLAM_EDGE_DAMAGE} at {@value #SLAM_RADIUS} blocks, and 0 beyond. This is on top of
     * the fall damage the slam itself causes.
     */
    public static float slamDamage(double distance) {
        if (distance > SLAM_RADIUS) {
            return 0.0F;
        }
        double d = Math.max(0.0D, distance);
        double falloff = (SLAM_CENTRE_DAMAGE - SLAM_EDGE_DAMAGE) * (d / SLAM_RADIUS);
        return (float) (SLAM_CENTRE_DAMAGE - falloff);
    }

    /** Health the Soul Scythe returns for {@code dealt} damage. Never negative. */
    public static float lifesteal(float dealt) {
        return dealt <= 0.0F ? 0.0F : dealt * LIFESTEAL_RATIO;
    }

    /** Damage the scythe's own sweep passes to a neighbour of the struck target. */
    public static float sweepDamage(float dealt) {
        return dealt <= 0.0F ? 0.0F : dealt * SWEEP_RATIO;
    }

    /** Absorption a player ends up with after one soul wisp arrives, capped. */
    public static float absorptionAfterKill(float current) {
        float base = Math.max(0.0F, current);
        return Math.min(base + ABSORPTION_PER_SOUL, ABSORPTION_CAP);
    }

    /**
     * Velocity for one grapple pull tick: straight at the anchor, fast enough to arrive within
     * {@code ticksLeft} ticks but never faster than {@value #GRAPPLE_MAX_SPEED} blocks per tick,
     * plus a constant upward nudge.
     *
     * @param toAnchor  vector from the player to the anchor point
     * @param ticksLeft ticks the pull has left; values below 1 are treated as 1
     */
    public static Vec3 grappleVelocity(Vec3 toAnchor, int ticksLeft) {
        double distance = toAnchor.length();
        if (distance < 1.0E-4D) {
            return new Vec3(0.0D, GRAPPLE_LIFT, 0.0D);
        }
        int ticks = Math.max(1, ticksLeft);
        double speed = Math.min(distance / ticks, GRAPPLE_MAX_SPEED);
        return toAnchor.normalize().scale(speed).add(0.0D, GRAPPLE_LIFT, 0.0D);
    }

    private DamageMath() {
    }
}
