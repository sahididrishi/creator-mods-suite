package dev.riftal.creator.features.events.util;

/**
 * The rising kill-plane of {@code voidrise}. Pure arithmetic so the timer maths is testable.
 */
public final class VoidPlane {

    /** One step of the rise, clamped so the plane never overshoots {@code maxY}. */
    public static double advance(double planeY, double speedPerTick, double maxY) {
        double next = planeY + Math.max(0.0D, speedPerTick);
        return Math.min(next, maxY);
    }

    /**
     * The per-tick speed that gets the plane from {@code fromY} to {@code toY} in {@code ticks}.
     * A non-positive tick count means "immediately", which is expressed as the whole distance.
     */
    public static double speedForTicks(double fromY, double toY, int ticks) {
        double distance = Math.max(0.0D, toY - fromY);
        if (ticks <= 0) {
            return Math.max(distance, 0.0D);
        }
        return distance / ticks;
    }

    /** How many ticks the plane still needs, or {@code -1} when it is standing still. */
    public static int ticksRemaining(double planeY, double maxY, double speedPerTick) {
        if (speedPerTick <= 0.0D) {
            return -1;
        }
        double distance = Math.max(0.0D, maxY - planeY);
        return (int) Math.ceil(distance / speedPerTick);
    }

    /** 0..1 progress of the rise, for the HUD bar. */
    public static float progress(double startY, double planeY, double maxY) {
        double span = maxY - startY;
        if (span <= 0.0D) {
            return 1.0F;
        }
        double done = (planeY - startY) / span;
        return (float) (done < 0.0D ? 0.0D : Math.min(done, 1.0D));
    }

    private VoidPlane() {
    }
}
