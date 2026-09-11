package dev.riftal.creator.features.evolve.client.hud;

import dev.riftal.creator.core.hud.HudLayers;
import dev.riftal.creator.core.hud.HudText;
import dev.riftal.creator.features.evolve.client.ClientEvolutionCache;
import dev.riftal.creator.features.evolve.data.EvolutionData;
import dev.riftal.creator.features.evolve.stage.EvolutionStage;
import dev.riftal.creator.features.evolve.stage.Stages;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Bottom-left evolution readout: stage name, progress bar, XP counter, floating "+N EVO" pop-ups,
 * and the white flash at the end of a transformation.
 *
 * <p>Client only. Registered through {@code HudLayers} from {@code EvolveFeature#initClient()}.
 */
public final class EvolutionHud {

    /** Same width as the vanilla XP bar, so it reads as part of the HUD. */
    private static final int BAR_WIDTH = 182;

    private static final int BAR_HEIGHT = 5;

    /** Distance from the left edge. */
    private static final int MARGIN_X = 10;

    /** Distance from the bottom edge to the top of the bar. */
    private static final int MARGIN_Y = 30;

    private static final int BAR_BACKGROUND = 0xC0101018;

    private static final int TEXT_DIM = 0xFFB0B0C0;

    /** Registered as a {@code HudLayer}: vanilla's {@code LayeredDraw.Layer} signature. */
    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (HudLayers.hudHidden()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ClientEvolutionCache.onLevel(minecraft.level);

        UUID self = minecraft.player.getUUID();
        EvolutionData state = ClientEvolutionCache.state(self);
        EvolutionStage stage = Stages.byOrdinal(state.stage());

        int barX = MARGIN_X;
        int barY = graphics.guiHeight() - MARGIN_Y;

        HudText.drawBar(graphics, barX, barY, BAR_WIDTH, BAR_HEIGHT, state.progress(),
                BAR_BACKGROUND, stage.colour());

        Component name = Component.translatable(stage.nameKey());
        HudText.drawShadowed(graphics, name, barX, barY - 11, stage.colour());

        String counter = state.stage() >= Stages.MAX
                ? "MAX"
                : state.xp() + " / " + Stages.thresholdFor(state.stage() + 1);
        HudText.drawShadowed(graphics, counter,
                barX + BAR_WIDTH - HudText.width(counter), barY - 11, TEXT_DIM);

        ClientEvolutionCache.Transform transform = ClientEvolutionCache.transform(self);
        if (transform != null || state.transforming()) {
            String label = Component.translatable("hud.creator_evolve.transforming").getString()
                    .toUpperCase(Locale.ROOT);
            HudText.drawShadowed(graphics, label, barX, barY - 22, 0xFFFFFFFF);
        }

        drawPopups(graphics, barX, barY);
        drawFlash(graphics, transform);
    }

    private static void drawPopups(GuiGraphics graphics, int barX, int barY) {
        List<ClientEvolutionCache.Popup> popups = ClientEvolutionCache.popups();
        if (popups.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int row = 0;
        for (int i = popups.size() - 1; i >= 0; i--) {
            ClientEvolutionCache.Popup popup = popups.get(i);
            float age = popup.age(now);
            int alpha = (int) (255.0F * (1.0F - age * age));
            if (alpha <= 8) {
                continue;
            }
            int rise = (int) (20.0F * age);
            int colour = (alpha << 24) | (ClientEvolutionCache.popupColour(popup.source()) & 0x00FFFFFF);
            String text = (popup.amount() >= 0 ? "+" : "") + popup.amount() + " EVO";
            HudText.drawShadowed(graphics, text, barX + BAR_WIDTH + 6, barY - rise - row * 10, colour);
            row++;
        }
    }

    private static void drawFlash(GuiGraphics graphics, ClientEvolutionCache.Transform transform) {
        if (transform == null) {
            return;
        }
        float strength = transform.flash(System.currentTimeMillis());
        if (strength <= 0.0F) {
            return;
        }
        int alpha = (int) (230.0F * Math.min(1.0F, strength));
        if (alpha <= 2) {
            return;
        }
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | 0x00FFFFFF);
    }

    private EvolutionHud() {
    }
}
