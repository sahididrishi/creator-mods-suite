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

    /** Clockwise pie wedge, shrinking towards twelve o'clock as the ability refills. */
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
            for (int cx = 0; cx < size; cx += CELL) {
                double dx = cx + CELL / 2.0D - centre;
                double dy = cy + CELL / 2.0D - centre;
                if (dx * dx + dy * dy > radiusSq) {
                    continue;
                }
                double angle = Math.atan2(dx, -dy);
                if (angle < 0.0D) {
                    angle += Math.PI * 2.0D;
                }
                if (angle < sweep) {
                    graphics.fill(x + cx, y + cy,
                            Math.min(x + size, x + cx + CELL), Math.min(y + size, y + cy + CELL), argb);
                }
            }
        }
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
