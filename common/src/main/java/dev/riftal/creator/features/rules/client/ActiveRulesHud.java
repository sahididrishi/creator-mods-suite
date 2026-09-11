package dev.riftal.creator.features.rules.client;

import dev.riftal.creator.core.hud.HudLayer;
import dev.riftal.creator.core.hud.HudLayers;
import dev.riftal.creator.core.hud.HudText;
import dev.riftal.creator.features.rules.api.Rule;
import dev.riftal.creator.features.rules.api.RuleRegistry;
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
 * (or the {@code creator_rules.rulesHud} gamerule) removes it entirely for thumbnails.
 *
 * <p>This layer is also where the client cache is dropped on a world change: the feature has no
 * client-tick hook of its own, so - exactly as {@code ClientPowers} does - the connection is
 * edge-detected here. Without it, leaving a world with rules on and joining one where the feature
 * is switched off left the previous world's list drawn on screen forever, because no sync ever
 * arrives to replace it.
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

    /** Last seen connection, compared by identity only. Never dereferenced. */
    private static Object connection;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        Object currentConnection = minecraft.getConnection();
        if (currentConnection != connection) {
            boolean hadPrevious = connection != null;
            connection = currentConnection;
            if (hadPrevious) {
                ClientRuleState.reset();
            }
        }
        if (HudLayers.hudHidden() || !ClientRuleState.hudVisible()) {
            return;
        }
        List<String> active = ClientRuleState.active();
        if (active.isEmpty()) {
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
            Rule rule = RuleRegistry.byId(id);
            String label = (rule == null
                    ? Component.translatable("rule.creator_rules." + id)
                    : rule.displayName()).getString();
            int colour = ClientRuleState.isHighlighted(id) ? HIGHLIGHT_COLOUR : ENTRY_COLOUR;
            HudText.drawShadowed(graphics, label, right - HudText.width(label), y, colour);
            y += LINE_HEIGHT;
        }
    }
}
