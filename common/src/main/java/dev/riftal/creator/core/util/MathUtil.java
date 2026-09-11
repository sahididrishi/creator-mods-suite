package dev.riftal.creator.core.util;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Small vector / box helpers used across the suite. Pure logic, unit-testable. */
public final class MathUtil {

    /** Cube of half-extent {@code radius} centred on {@code centre}. */
    public static AABB boxAround(Vec3 centre, double radius) {
        return new AABB(centre.x - radius, centre.y - radius, centre.z - radius,
                centre.x + radius, centre.y + radius, centre.z + radius);
    }

    /** Cube of half-extent {@code radius} centred on the middle of {@code pos}. */
    public static AABB boxAround(BlockPos pos, double radius) {
        return boxAround(Vec3.atCenterOf(pos), radius);
    }

    /** Distance ignoring Y. */
    public static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Unit vector the entity is looking along. */
    public static Vec3 look(Entity entity) {
        return entity.getLookAngle().normalize();
    }

    /** Unit vector the entity is looking along, flattened onto the XZ plane. */
    public static Vec3 horizontalLook(Entity entity) {
        Vec3 look = entity.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        return flat.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
    }

    /** {@code n} evenly spaced points on a horizontal circle. */
    public static Vec3[] ring(Vec3 centre, double radius, int points) {
        Vec3[] out = new Vec3[Math.max(1, points)];
        for (int i = 0; i < out.length; i++) {
            double angle = (Math.PI * 2.0D * i) / out.length;
            out[i] = centre.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
        }
        return out;
    }

    /** Uniform-ish random point inside the sphere of {@code radius} around {@code centre}. */
    public static Vec3 randomInSphere(RandomSource random, Vec3 centre, double radius) {
        double r = radius * Math.cbrt(random.nextDouble());
        double theta = random.nextDouble() * Math.PI * 2.0D;
        double phi = Math.acos(2.0D * random.nextDouble() - 1.0D);
        return centre.add(r * Math.sin(phi) * Math.cos(theta),
                r * Math.cos(phi),
                r * Math.sin(phi) * Math.sin(theta));
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double lerp(double t, double from, double to) {
        return from + (to - from) * t;
    }

    /** Ticks formatted as {@code m:ss}, for HUD timers. */
    public static String formatTicks(int ticks) {
        int totalSeconds = Math.max(0, ticks) / 20;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private MathUtil() {
    }
}
