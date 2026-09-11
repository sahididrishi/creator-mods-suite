package dev.riftal.creator.features.toolkit.wave;

/**
 * Placement maths for {@code /toolkit wave spawn}. Pure Java on purpose - no Minecraft types, no
 * registries - so it can be unit tested without booting the game.
 */
public final class WaveMath {

    /** How a wave is laid out around its centre. */
    public enum Mode {
        /** Evenly spaced on a circle of exactly {@code radius}, every mob facing the centre. */
        RING,
        /** Uniformly scattered inside the disc of {@code radius}. */
        RANDOM;

        /** Parses a command literal. Unknown values fall back to {@link #RING}. */
        public static Mode parse(String name) {
            return "random".equalsIgnoreCase(name) ? RANDOM : RING;
        }

        /** Lowercase name, for command literals and feedback. */
        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Angle, in radians, of ring slot {@code index} of {@code count}. */
    public static double ringAngle(int index, int count) {
        int n = Math.max(1, count);
        return (Math.PI * 2.0D * Math.floorMod(index, n)) / n;
    }

    /** X offset from the centre for a point at {@code angle} on a circle of {@code radius}. */
    public static double offsetX(double angle, double radius) {
        return Math.cos(angle) * radius;
    }

    /** Z offset from the centre for a point at {@code angle} on a circle of {@code radius}. */
    public static double offsetZ(double angle, double radius) {
        return Math.sin(angle) * radius;
    }

    /**
     * Radius of a uniformly distributed point in a disc.
     *
     * @param unitRandom a value in {@code [0, 1)}
     */
    public static double discRadius(double unitRandom, double radius) {
        return radius * Math.sqrt(Math.max(0.0D, Math.min(1.0D, unitRandom)));
    }

    /**
     * Minecraft yaw, in degrees, for an entity that should look along {@code (dx, dz)}.
     *
     * <p>Same formula as vanilla {@code Entity#lookAt}:
     * {@code wrapDegrees(atan2(dz, dx) * 180 / PI - 90)}.
     */
    public static float yawAlong(double dx, double dz) {
        return wrapDegrees((float) (Math.atan2(dz, dx) * 180.0D / Math.PI - 90.0D));
    }

    /**
     * Yaw for a mob standing at {@code centre + (offsetX, offsetZ)} that should face the centre.
     */
    public static float yawTowardCentre(double offsetX, double offsetZ) {
        return yawAlong(-offsetX, -offsetZ);
    }

    /** Vanilla {@code Mth#wrapDegrees}, reimplemented so this class stays Minecraft-free. */
    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    private WaveMath() {
    }
}
