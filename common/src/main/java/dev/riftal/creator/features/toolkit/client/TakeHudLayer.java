package dev.riftal.creator.features.toolkit.client;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.core.hud.HudLayer;
import dev.riftal.creator.core.hud.HudLayers;
import dev.riftal.creator.core.hud.HudText;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import dev.riftal.creator.features.toolkit.take.TakeFormat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The clapperboard, top left. <strong>Client only.</strong>
 *
 * <pre>
 * REC TAKE 003  00:12.3
 *     MARK 2
 *     FROZEN mobs
 * </pre>
 *
 * <p>Honours F1 like every other HUD layer, which is also what {@code /toolkit hide hud on} uses -
 * so a "clean frame" really is clean, timer included.
 *
 * <p>Every string is a translation key resolved to text, so the widths {@link HudText#width(String)}
 * measures are the widths actually drawn.
 */
public final class TakeHudLayer implements HudLayer {

    /** Translation key prefix; every key below has a line in {@code lang/en_us.json}. */
    private static final String KEY = "hud." + ToolkitFeature.NAMESPACE + ".";

    private static final int X = 6;
    private static final int Y = 6;
    private static final int LINE = 10;

    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFFF5555;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int AQUA = 0xFF55FFFF;
    private static final int GREY = 0xFFAAAAAA;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!CreatorMods.isEnabled(ToolkitFeature.ID)) {
            return;
        }
        ClientToolkitState.requestSyncIfNeeded();
        if (HudLayers.hudHidden()) {
            return;
        }

        boolean running = ClientToolkitState.running();
        int y = Y;

        // ASCII only in en_us: the vanilla font sheet has no guaranteed glyph for a filled circle.
        String badge = text(running ? "recording" : "idle") + " ";
        HudText.drawShadowed(graphics, badge, X, y, running ? RED : GREY);
        int offset = HudText.width(badge);

        String line = text("take", TakeFormat.takeNumber(ClientToolkitState.takeNumber()),
                TakeFormat.formatRta(ClientToolkitState.rtaMillis()));
        HudText.drawShadowed(graphics, line, X + offset, y, running ? WHITE : GREY);
        y += LINE;

        int flash = ClientToolkitState.markFlash();
        if (flash > 0) {
            HudText.drawShadowed(graphics, text("mark", flash), X + offset, y, YELLOW);
            y += LINE;
        } else if (!running && ClientToolkitState.markCount() > 0) {
            HudText.drawShadowed(graphics, text("marks", ClientToolkitState.markCount()),
                    X + offset, y, GREY);
            y += LINE;
        }

        String frozen = frozenLabel();
        if (frozen != null) {
            HudText.drawShadowed(graphics, frozen, X + offset, y, AQUA);
        }
    }

    private static String frozenLabel() {
        boolean mobs = ClientToolkitState.mobsFrozen();
        boolean players = ClientToolkitState.playersFrozen();
        if (mobs && players) {
            return text("frozen.both");
        }
        if (mobs) {
            return text("frozen.mobs");
        }
        if (players) {
            return text("frozen.players");
        }
        return null;
    }

    /** Resolves one {@code hud.creator_toolkit.*} key to drawable text. */
    private static String text(String suffix, Object... args) {
        return Component.translatable(KEY + suffix, args).getString();
    }
}
