package dev.riftal.creator.features.events.client;

import dev.riftal.creator.core.hud.HudLayers;
import dev.riftal.creator.core.hud.HudText;
import dev.riftal.creator.core.util.MathUtil;
import dev.riftal.creator.features.events.net.EventStatePayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The director's one HUD line, top centre, under the boss bar.
 *
 * <p><b>Client only.</b> Registered from {@code EventsFeature#initClient()}.
 */
public final class EventHud {

    /** Top offset in pixels. Clears one vanilla boss bar. */
    private static final int TOP = 30;

    private static final int TEXT_ARGB = 0xFFFFFFFF;
    private static final int BAR_BG_ARGB = 0x80000000;
    private static final int BAR_FILL_ARGB = 0xFFE04040;
    private static final int BAR_WIDTH = 120;

    /** The {@code HudLayer} body. */
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (HudLayers.hudHidden() || !ClientEventState.hudVisible()) {
            return;
        }
        EventStatePayload state = ClientEventState.state();
        Component line = lineFor(state);
        int centreX = graphics.guiWidth() / 2;
        HudText.drawCentered(graphics, line.getString(), centreX, TOP, TEXT_ARGB);
        if (state.progress() > 0.0F) {
            HudText.drawBar(graphics, centreX - BAR_WIDTH / 2, TOP + 11, BAR_WIDTH, 3,
                    state.progress(), BAR_BG_ARGB, BAR_FILL_ARGB);
        }
    }

    /**
     * The text for one state. Split out from {@link #render} so it can be reasoned about (and read)
     * without a {@code GuiGraphics}.
     */
    public static Component lineFor(EventStatePayload state) {
        String key = "hud.creator_events." + state.eventId();
        return switch (state.eventId()) {
            case "siege" -> Component.translatable(key, state.wave(), state.waveTotal(), state.alive());
            case "voidrise" -> Component.translatable(key, formatY(state.voidY()));
            case "meteor" -> Component.translatable(key, remaining(state));
            case "luckyrain" -> Component.translatable(key, remaining(state));
            default -> Component.translatable(key);
        };
    }

    private static String remaining(EventStatePayload state) {
        if (state.phaseDuration() < 0) {
            return "-";
        }
        return MathUtil.formatTicks(Math.max(0, state.phaseDuration() - state.phaseTick()));
    }

    private static String formatY(double y) {
        return Double.isNaN(y) ? "-" : Integer.toString((int) Math.floor(y));
    }

    private EventHud() {
    }
}
