package dev.riftal.creator.core.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Two-line text and bar helpers shared by the take timer, the cooldown row and the rule list.
 *
 * <p><b>Client only.</b>
 */
public final class HudText {

    /** Drop-shadowed string at {@code (x, y)} with an ARGB colour. */
    public static void drawShadowed(GuiGraphics graphics, String text, int x, int y, int argb) {
        graphics.drawString(Minecraft.getInstance().font, text, x, y, argb, true);
    }

    /** Drop-shadowed component at {@code (x, y)}. */
    public static void drawShadowed(GuiGraphics graphics, Component text, int x, int y, int argb) {
        graphics.drawString(Minecraft.getInstance().font, text, x, y, argb, true);
    }

    /** Drop-shadowed string centred on {@code x}. */
    public static void drawCentered(GuiGraphics graphics, String text, int x, int y, int argb) {
        int width = Minecraft.getInstance().font.width(text);
        drawShadowed(graphics, text, x - width / 2, y, argb);
    }

    /** Width of {@code text} in pixels, for laying out your own rows. */
    public static int width(String text) {
        return Minecraft.getInstance().font.width(text);
    }

    /**
     * A filled progress bar.
     *
     * @param progress 0..1, clamped
     */
    public static void drawBar(GuiGraphics graphics, int x, int y, int width, int height,
                               float progress, int backgroundArgb, int fillArgb) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        graphics.fill(x, y, x + width, y + height, backgroundArgb);
        graphics.fill(x, y, x + (int) (width * clamped), y + height, fillArgb);
    }

    private HudText() {
    }
}
