package dev.riftal.creator.features.powers.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.riftal.creator.core.hud.HudLayer;
import dev.riftal.creator.core.hud.HudLayers;
import dev.riftal.creator.core.hud.HudText;
import dev.riftal.creator.features.powers.PowersFeature;
import dev.riftal.creator.features.powers.ability.Ability;
import dev.riftal.creator.features.powers.ability.AbilityRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The cooldown row: one slot per granted ability, centred above the hotbar, with a sweep that
 * empties as the ability comes back.
 *
 * <p><b>Client only.</b>
 *
 * <p>Nothing is drawn at all when the player has no abilities - an empty row of six grey boxes on
 * a fresh world is exactly the kind of permanent clutter this suite is supposed to avoid.
 *
 * <p>Art: a 22x22 frame sprite per slot (the top-left corner of the 32x32 {@code slot.png} /
 * {@code slot_ready.png} sheets, tinted with the ability's accent colour once it is ready) and a
 * 16x16 ability icon inside it. The cooldown sweep on top stays procedural - see
 * {@link SweepRenderer}.
 */
public final class PowerHudLayer implements HudLayer {

    /** Interior of one slot; the frame sprite is one pixel larger on every side. */
    private static final int SLOT = 20;
    private static final int FRAME = 22;
    private static final int ICON = 16;
    private static final int GAP = 2;

    /**
     * The frame sprites are 32x32 sheets carrying the 22x22 frame in their top-left corner -
     * textures are power-of-two per CONTRACT.md section 9.1, so the blit has to name the sheet
     * size rather than the drawn size.
     */
    private static final int FRAME_SHEET = 32;

    /** Above the 22 px hotbar, clear of the jump/health rows. */
    private static final int BOTTOM_MARGIN = 52;

    private static final ResourceLocation SLOT_TEXTURE = PowersFeature.guiTexture("slot");
    private static final ResourceLocation SLOT_READY_TEXTURE = PowersFeature.guiTexture("slot_ready");

    private static final int SWEEP = 0xC0101018;
    private static final int FLASH = 0x90FFFFFF;
    private static final int SECONDS = 0xFFFFFFFF;
    private static final int KEY_LABEL = 0xFFB0B0BC;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        // Last-resort fallback for a loader whose glue never claimed the per-tick job - on Fabric
        // that glue is reached reflectively and could in principle fail to start. It is NOT a
        // safety net on NeoForge: vanilla only runs the layered draw inside its !hideGui guard, so
        // this whole method is skipped while F1 is on. Both loaders wire a real client-tick hook
        // (PowersFabricClientGlue / PowersNeoForgeClientTick) and this does nothing there.
        PowersClient.tickIfNotWired();

        if (HudLayers.hudHidden() || !ClientPowers.hudVisible()) {
            return;
        }
        List<ResourceLocation> granted = ClientPowers.granted();
        if (granted.isEmpty()) {
            return;
        }

        int count = granted.size();
        int totalWidth = count * SLOT + (count - 1) * GAP;
        int x0 = (graphics.guiWidth() - totalWidth) / 2;
        int y = graphics.guiHeight() - BOTTOM_MARGIN;

        // The frame and icon sprites have transparent pixels; innerBlit does not touch the blend
        // state, so turn it on around the whole row rather than per slot.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int slot = 0; slot < count; slot++) {
            ResourceLocation id = granted.get(slot);
            // A freshly granted slot lands a few ticks after the one before it (plan 03 beat 1).
            // Its place in the row is reserved from the start, so the icons pop in one by one
            // instead of the whole row sliding sideways under the ones already on screen.
            if (!ClientPowers.appeared(id)) {
                continue;
            }
            Ability ability = AbilityRegistry.get(id).orElse(null);
            int x = x0 + slot * (SLOT + GAP);
            if (ClientPowers.isShaking(slot)) {
                // Two-pixel nudge that alternates every tick: a clear "no" without a sound.
                x += (ClientPowers.now() % 2L == 0L) ? 2 : -2;
            }
            drawSlot(graphics, x, y, slot, id, ability);
        }
        RenderSystem.disableBlend();
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y, int slot,
                                 ResourceLocation id, Ability ability) {
        boolean ready = ClientPowers.isReady(id);

        // The ready frame is drawn in the ability's own accent (the same colour as its icon), so a
        // glance at the row says *which* slot just came back without reading six small sprites.
        if (ready && ability != null) {
            int tint = ability.hudColor();
            graphics.setColor(((tint >> 16) & 0xFF) / 255.0F,
                    ((tint >> 8) & 0xFF) / 255.0F,
                    (tint & 0xFF) / 255.0F,
                    1.0F);
        }
        graphics.blit(ready ? SLOT_READY_TEXTURE : SLOT_TEXTURE,
                x - 1, y - 1, 0.0F, 0.0F, FRAME, FRAME, FRAME_SHEET, FRAME_SHEET);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (ability != null) {
            graphics.blit(ability.icon(), x + 2, y + 2, 0.0F, 0.0F, ICON, ICON, ICON, ICON);
        }

        float remaining = ClientPowers.remainingFraction(id);
        if (remaining > 0.0F) {
            if (ClientPowers.hudRadial()) {
                SweepRenderer.radial(graphics, x, y, SLOT, remaining, SWEEP);
            } else {
                SweepRenderer.linear(graphics, x, y, SLOT, remaining, SWEEP);
            }
            int seconds = (ClientPowers.remainingTicks(id) + 19) / 20;
            HudText.drawCentered(graphics, Integer.toString(seconds), x + SLOT / 2, y + 7, SECONDS);
        }

        if (ClientPowers.isFlashing(id)) {
            graphics.fill(x, y, x + SLOT, y + SLOT, FLASH);
        }

        KeyMapping key = PowerKeys.forSlot(slot);
        if (key != null) {
            String label = key.getTranslatedKeyMessage().getString();
            if (label.length() <= 3) {
                HudText.drawShadowed(graphics, label, x + 1, y + SLOT - 8, KEY_LABEL);
            }
        }
    }
}
