package dev.riftal.creator.features.events.util;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Uniform sampling of a horizontal annulus - where siege waves and lucky rain come down.
 *
 * <p>Uniform in <em>area</em>, not in radius: sampling {@code r} linearly would bunch every spawn
 * against the inner edge. Pure math, so {@code SpawnRingMathTest} can hammer it without a level.
 */
public final class SpawnRing {

    /**
     * A point on the horizontal ring around {@code centre}, uniform over the annulus area.
     *
     * @param minRadius inclusive inner radius, clamped to {@code >= 0}
     * @param maxRadius outer radius; if it is below {@code minRadius} the two swap
     */
    public static Vec3 sample(RandomSource random, Vec3 centre, double minRadius, double maxRadius) {
        double min = Math.max(0.0D, Math.min(minRadius, maxRadius));
        double max = Math.max(minRadius, maxRadius);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = radiusFor(random.nextDouble(), min, max);
        return new Vec3(centre.x + Math.cos(angle) * radius, centre.y, centre.z + Math.sin(angle) * radius);
    }

    /**
     * The radius an area-uniform sample lands at.
     *
     * @param roll 0..1 uniform
     */
    public static double radiusFor(double roll, double minRadius, double maxRadius) {
        double min = Math.max(0.0D, Math.min(minRadius, maxRadius));
        double max = Math.max(minRadius, maxRadius);
        double clamped = roll < 0.0D ? 0.0D : Math.min(roll, 1.0D);
        return Math.sqrt(min * min + clamped * (max * max - min * min));
    }

    private SpawnRing() {
    }
}
