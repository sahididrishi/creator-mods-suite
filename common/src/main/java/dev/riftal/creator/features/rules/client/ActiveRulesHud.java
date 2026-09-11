package dev.riftal.creator.features.rules.client;

import dev.riftal.creator.core.hud.HudLayer;
import dev.riftal.creator.core.hud.HudLayers;
import dev.riftal.creator.core.hud.HudText;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The top-right "RULES" list.
 *
 * <p>Right-aligned so it never fights the Director's Toolkit take timer in the top left, and pushed
 * down while the tab list is open. F1 hides it like any other HUD element, and {@code /rule hud off}
 * removes it entirely for thumbnails.
 *
 * <p><b>Client only.</b>
 */
public final class ActiveRulesHud implements HudLayer {

    private static final int HEADER_COLOUR = 0xFFFFAA00;
    private static final int ENTRY_COLOUR = 0xFFFFFFFF;
    private static final int HIGHLIGHT_COLOUR = 0xFFFFFF55;
    private static final int MARGIN = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int TAB_LIST_OFFSET = 10;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (HudLayers.hudHidden() || !ClientRuleState.hudVisible()) {
            return;
        }
        List<String> active = ClientRuleState.active();
        if (active.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        int right = minecraft.getWindow().getGuiScaledWidth() - MARGIN;
        int y = MARGIN;
        if (minecraft.options.keyPlayerList.isDown()) {
            y += TAB_LIST_OFFSET;
        }

        String header = Component.translatable("hud.creator_rules.header").getString();
        HudText.drawShadowed(graphics, header, right - HudText.width(header), y, HEADER_COLOUR);
        y += LINE_HEIGHT + 1;

        for (String id : active) {
            String label = Component.translatable("rule.creator_rules." + id).getString();
            int colour = ClientRuleState.isHighlighted(id) ? HIGHLIGHT_COLOUR : ENTRY_COLOUR;
            HudText.drawShadowed(graphics, label, right - HudText.width(label), y, colour);
            y += LINE_HEIGHT;
        }
    }
}
