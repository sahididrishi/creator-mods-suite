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
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /**
     * How close you have to be to <em>someone else's</em> transformation to catch the flash, in
     * blocks. The plan asks for 8; the strength falls off linearly to nothing at the edge, so a
     * witness gets a wash rather than the full white-out the subject gets.
     */
    public static final double WITNESS_FLASH_RADIUS = 8.0D;

    /** Registered as a {@code HudLayer}: vanilla's {@code LayeredDraw.Layer} signature. */
    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        // Above the F1 guard on purpose: PlayerRenderSwap reads the same cache, so which world's
        // stages are live must not depend on whether the HUD happens to be drawing this frame.
        ClientEvolutionCache.onLevel(minecraft.level);

        if (HudLayers.hudHidden() || minecraft.player == null) {
            return;
        }

        UUID self = minecraft.player.getUUID();
        EvolutionData state = ClientEvolutionCache.state(self);
        ClientEvolutionCache.Transform transform = ClientEvolutionCache.transform(self);
        boolean midSequence = transform != null && transform.running();

        // The server banks the new stage at tick 0 so nothing can lose it, which means the raw
        // sync already says "APEX" while the player is still standing there locked. Keep showing
        // the rung they are leaving until the STOP lands, so the title card gets to break the news.
        int shownStage = midSequence
                ? Stages.clamp(transform.targetStage() - 1)
                : state.stage();
        EvolutionStage stage = Stages.byOrdinal(shownStage);
        float progress = midSequence ? 1.0F : state.progress();

        int barX = MARGIN_X;
        int barY = graphics.guiHeight() - MARGIN_Y;

        HudText.drawBar(graphics, barX, barY, BAR_WIDTH, BAR_HEIGHT, progress,
                BAR_BACKGROUND, stage.colour());

        Component name = Component.translatable(stage.nameKey());
        HudText.drawShadowed(graphics, name, barX, barY - 11, stage.colour());

        String counter = counterFor(shownStage, state.xp(), midSequence);
        HudText.drawShadowed(graphics, counter,
                barX + BAR_WIDTH - HudText.width(counter), barY - 11, TEXT_DIM);

        if (midSequence || state.transforming()) {
            String label = Component.translatable("hud.creator_evolve.transforming").getString()
                    .toUpperCase(Locale.ROOT);
            int colour = midSequence
                    ? Stages.byOrdinal(transform.targetStage()).colour()
                    : 0xFFFFFFFF;
            HudText.drawShadowed(graphics, label, barX, barY - 22, colour);
        }

        drawPopups(graphics, barX, barY);
        drawFlash(graphics, minecraft, self);
    }

    private static String counterFor(int shownStage, int xp, boolean midSequence) {
        if (midSequence) {
            return "READY";
        }
        return shownStage >= Stages.MAX
                ? "MAX"
                : xp + " / " + Stages.thresholdFor(shownStage + 1);
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

    /**
     * The white-out. Full strength for your own transformation; for anyone else's, scaled by how
     * close you are, which is what the payload is broadcast to trackers for in the first place.
     */
    private static void drawFlash(GuiGraphics graphics, Minecraft minecraft, UUID self) {
        long now = System.currentTimeMillis();
        float strength = 0.0F;

        for (Map.Entry<UUID, ClientEvolutionCache.Transform> entry
                : ClientEvolutionCache.transforms().entrySet()) {
            float raw = entry.getValue().flash(now);
            if (raw <= 0.0F) {
                continue;
            }
            strength = Math.max(strength, entry.getKey().equals(self)
                    ? raw
                    : raw * witnessFalloff(minecraft, entry.getKey()));
        }

        if (strength <= 0.0F) {
            return;
        }
        int alpha = (int) (230.0F * Math.min(1.0F, strength));
        if (alpha <= 2) {
            return;
        }
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | 0x00FFFFFF);
    }

    /** 1 at the transforming player's feet, 0 at {@link #WITNESS_FLASH_RADIUS} blocks and beyond. */
    private static float witnessFalloff(Minecraft minecraft, UUID playerId) {
        if (minecraft.level == null || minecraft.player == null) {
            return 0.0F;
        }
        Player other = minecraft.level.getPlayerByUUID(playerId);
        if (other == null) {
            return 0.0F;
        }
        double distance = minecraft.player.distanceTo(other);
        if (distance >= WITNESS_FLASH_RADIUS) {
            return 0.0F;
        }
        return (float) (1.0D - distance / WITNESS_FLASH_RADIUS);
    }

    private EvolutionHud() {
    }
}
