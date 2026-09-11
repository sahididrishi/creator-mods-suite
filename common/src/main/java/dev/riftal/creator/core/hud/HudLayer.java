package dev.riftal.creator.core.hud;

import net.minecraft.client.gui.LayeredDraw;

/**
 * One HUD element. Vanilla's {@code LayeredDraw.Layer}, i.e.
 * {@code render(GuiGraphics, DeltaTracker)} on both loaders.
 *
 * <p><b>Client only.</b> Referencing this type from common code that runs on a dedicated server
 * throws {@code NoClassDefFoundError}. Touch it from {@code Feature#initClient()} and nowhere else.
 */
@FunctionalInterface
public interface HudLayer extends LayeredDraw.Layer {
}
