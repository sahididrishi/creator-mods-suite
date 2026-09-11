package dev.riftal.creator.features.powers.data;

/**
 * Pure cooldown / falloff arithmetic for the {@code powers} feature.
 *
 * <p>No game types, no registries, no level: every method here is a function of numbers, which is
 * what lets the balance be unit-tested without booting Minecraft.
 *
 * <p>The cooldown model is the one Apoli uses: store the <em>absolute</em> game tick an ability
 * becomes ready again, never a countdown. Absolute ticks survive a relog and never drift when the
 * server lags.
 */
public final class CooldownMath {

    /** Absolute tick at which an ability used at {@code now} becomes ready again. */
    public static long readyAt(long now, int cooldownTicks) {
        return now + Math.max(0, cooldownTicks);
    }

    /** Ticks still to run, clamped to zero. */
    public static int remaining(long readyAt, long now) {
        if (now >= readyAt) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, readyAt - now);
    }

    /**
     * Progress of the cooldown, {@code 0} the instant it started and {@code 1} once it is ready.
     * A zero-length cooldown reads as fully ready instead of dividing by zero.
     */
    public static float fraction(long startedAt, long readyAt, long now) {
        long length = readyAt - startedAt;
        if (length <= 0L) {
            return 1.0F;
        }
        float progress = (now - startedAt) / (float) length;
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    /** The part of the sweep still greyed out: {@code 1 - fraction}. */
    public static float sweepRemaining(long startedAt, long readyAt, long now) {
        return 1.0F - fraction(startedAt, readyAt, now);
    }

    /**
     * Ground Pound damage falloff: full damage at the epicentre, a quarter at the rim, never below
     * a quarter inside the radius and zero outside it.
     */
    public static float poundFalloff(double distance, double radius) {
        if (radius <= 0.0D) {
            return 0.0F;
        }
        if (distance > radius) {
            return 0.0F;
        }
        double linear = 1.0D - distance / radius;
        return (float) Math.max(0.25D, Math.min(1.0D, linear));
    }

    /**
     * Cone test for Fire Burst. {@code look} and {@code toTarget} must both be unit vectors;
     * {@code cosHalfAngle} is {@code Math.cos(halfAngleRadians)}.
     */
    public static boolean inCone(double lookX, double lookY, double lookZ,
                                 double toX, double toY, double toZ,
                                 double cosHalfAngle) {
        double toLength = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
        if (toLength < 1.0E-6D) {
            return true;
        }
        double dot = (lookX * toX + lookY * toY + lookZ * toZ) / toLength;
        return dot >= cosHalfAngle;
    }

    private CooldownMath() {
    }
}
