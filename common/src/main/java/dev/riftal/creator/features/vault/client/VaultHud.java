package dev.riftal.creator.features.vault.client;

import dev.riftal.creator.core.hud.HudLayers;
import dev.riftal.creator.core.hud.HudText;
import dev.riftal.creator.core.util.MathUtil;
import dev.riftal.creator.features.vault.block.AltarState;
import dev.riftal.creator.features.vault.block.AltarStateMachine;
import dev.riftal.creator.features.vault.net.VaultHudState;
import dev.riftal.creator.features.vault.net.VaultStatusPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/**
 * Top-left readout for whichever Cursed Altar the player is standing near: its state, the charge
 * countdown, how many Sealed Chests are wired to it, and the Keeper's health.
 *
 * <p>Fed entirely by {@link VaultStatusPayload}, which the altar only sends while it is doing
 * something, so the HUD disappears on its own once the payload goes stale.
 *
 * <p><b>Client only.</b> Reached exclusively from {@code VaultFeature#initClient()}.
 */
public final class VaultHud {

    private static final int X = 8;
    private static final int Y = 8;
    private static final int BAR_WIDTH = 92;
    private static final int BAR_HEIGHT = 4;

    private static final int COLOUR_LABEL = 0xFFD8C6FF;
    private static final int COLOUR_DETAIL = 0xFF9B8BC4;
    private static final int COLOUR_BAR_BG = 0x80100018;
    private static final int COLOUR_CHARGE = 0xFFB050FF;
    private static final int COLOUR_KEEPER = 0xFFFF5555;

    /**
     * The level the cache belongs to. When the client changes world - a disconnect, a rejoin, a
     * dimension change - the cached altar status is from somewhere else and has to go, or the last
     * world's readout hangs around for the first two seconds of the next one.
     */
    @Nullable
    private static ClientLevel cachedFor;

    /** {@code HudLayer} / {@code LayeredDraw.Layer} entry point. */
    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (HudLayers.hudHidden()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != cachedFor) {
            VaultHudState.clear();
            cachedFor = minecraft.level;
        }
        if (minecraft.player == null) {
            return;
        }
        // Nearest altar to the camera, not "whichever packet arrived last": two altars inside the
        // 32-block broadcast range are a supported set-up and would otherwise flicker.
        VaultStatusPayload status = VaultHudState.current(minecraft.player.blockPosition());
        if (status == null) {
            return;
        }
        AltarState state = status.state();

        HudText.drawShadowed(graphics,
                Component.translatable("hud.creator_vault.altar",
                        Component.translatable("hud.creator_vault.state." + state.getSerializedName())),
                X, Y, COLOUR_LABEL);

        int line = Y + 11;
        if (state == AltarState.CHARGING) {
            float progress = AltarStateMachine.chargeProgress(state, status.chargeTicks());
            HudText.drawBar(graphics, X, line, BAR_WIDTH, BAR_HEIGHT, progress, COLOUR_BAR_BG, COLOUR_CHARGE);
            line += BAR_HEIGHT + 4;
            int remaining = Math.max(0, AltarStateMachine.CHARGE_TICKS - status.chargeTicks());
            HudText.drawShadowed(graphics,
                    Component.translatable("hud.creator_vault.charging", MathUtil.formatTicks(remaining)),
                    X, line, COLOUR_DETAIL);
            line += 11;
        } else if (state == AltarState.ACTIVE && status.keeperHealth() >= 0.0F) {
            float health = (float) MathUtil.clamp(status.keeperHealth(), 0.0D, 1.0D);
            HudText.drawBar(graphics, X, line, BAR_WIDTH, BAR_HEIGHT, health, COLOUR_BAR_BG, COLOUR_KEEPER);
            line += BAR_HEIGHT + 4;
            HudText.drawShadowed(graphics,
                    Component.translatable("hud.creator_vault.keeper", Math.round(health * 100.0F)),
                    X, line, COLOUR_DETAIL);
            line += 11;
        }

        HudText.drawShadowed(graphics,
                Component.translatable("hud.creator_vault.chests", status.sealedChests(), status.keysUsed()),
                X, line, COLOUR_DETAIL);
    }

    private VaultHud() {
    }
}
