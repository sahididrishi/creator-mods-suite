package dev.riftal.creator.features.powers.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The cooldown overlay for one HUD slot, drawn with nothing but {@code GuiGraphics#fill} so it
 * needs no texture, no custom render type and no {@code BufferBuilder} lifecycle - which keeps it
 * working identically on both loaders and under Sodium/Embeddium.
 *
 * <p><b>Client only.</b>
 *
 * <p>{@code remaining} is the fraction of the cooldown still to run: 1 the instant the ability
 * fires, 0 when it is ready.
 */
public final class SweepRenderer {

    /** Cell size of the radial approximation. 2 px reads as a clean pie at any GUI scale. */
    private static final int CELL = 2;

    /**
     * Clockwise pie wedge, shrinking towards twelve o'clock as the ability refills.
     *
     * <p>Lit cells are merged into horizontal runs and emitted as one {@code fill} per run.
     * {@code GuiGraphics#fill} on the HUD's unmanaged graphics ends in {@code flushIfUnmanaged()},
     * i.e. one {@code endBatch} per call, so a cell-at-a-time wedge is ~100 flushed quads per slot
     * and ~600 per frame with all six sweeps running - which is exactly the shot this feature
     * exists for (plan 03 beat 8). A wedge is convex, so each row is at most two runs and the merge
     * brings that down to roughly a tenth.
     */
    public static void radial(GuiGraphics graphics, int x, int y, int size, float remaining, int argb) {
        if (remaining <= 0.0F) {
            return;
        }
        if (remaining >= 1.0F) {
            graphics.fill(x, y, x + size, y + size, argb);
            return;
        }
        double sweep = remaining * Math.PI * 2.0D;
        double centre = size / 2.0D;
        double radiusSq = centre * centre;
        for (int cy = 0; cy < size; cy += CELL) {
            int runStart = -1;
            for (int cx = 0; cx < size; cx += CELL) {
                if (lit(cx, cy, centre, radiusSq, sweep)) {
                    if (runStart < 0) {
                        runStart = cx;
                    }
                    continue;
                }
                if (runStart >= 0) {
                    fillRun(graphics, x, y, size, runStart, cx, cy, argb);
                    runStart = -1;
                }
            }
            if (runStart >= 0) {
                fillRun(graphics, x, y, size, runStart, size, cy, argb);
            }
        }
    }

    /** Is the cell whose top-left is ({@code cx},{@code cy}) inside the circle and the wedge? */
    private static boolean lit(int cx, int cy, double centre, double radiusSq, double sweep) {
        double dx = cx + CELL / 2.0D - centre;
        double dy = cy + CELL / 2.0D - centre;
        if (dx * dx + dy * dy > radiusSq) {
            return false;
        }
        double angle = Math.atan2(dx, -dy);
        if (angle < 0.0D) {
            angle += Math.PI * 2.0D;
        }
        return angle < sweep;
    }

    /** One horizontal run of lit cells, from {@code fromCx} up to (not including) {@code toCx}. */
    private static void fillRun(GuiGraphics graphics, int x, int y, int size,
                                int fromCx, int toCx, int cy, int argb) {
        graphics.fill(x + fromCx, y + cy,
                Math.min(x + size, x + toCx), Math.min(y + size, y + cy + CELL), argb);
    }

    /** Bottom-up bar, the readable fallback at GUI scale 1 or with a very small icon row. */
    public static void linear(GuiGraphics graphics, int x, int y, int size, float remaining, int argb) {
        if (remaining <= 0.0F) {
            return;
        }
        int height = Math.max(1, Math.round(Math.min(1.0F, remaining) * size));
        graphics.fill(x, y + size - height, x + size, y + size, argb);
    }

    private SweepRenderer() {
    }
}
