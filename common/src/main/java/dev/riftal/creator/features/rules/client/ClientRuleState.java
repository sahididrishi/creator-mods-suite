package dev.riftal.creator.features.rules.client;

import dev.riftal.creator.features.rules.net.RuleToastPayload;
import dev.riftal.creator.features.rules.net.RulesSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The client's mirror of the server's active rule set: just enough to draw the HUD list.
 *
 * <p><b>Client only.</b> Reached exclusively from {@code RulesFeature#initClient()}.
 *
 * <p>Nothing here is authoritative - it is a display cache, refreshed by every
 * {@link RulesSyncPayload}.
 */
public final class ClientRuleState {

    /** How long a freshly toggled rule stays highlighted, in milliseconds (about 40 ticks). */
    private static final long HIGHLIGHT_MILLIS = 2000L;

    private static List<String> active = List.of();
    private static boolean hud = true;
    private static final Map<String, Long> HIGHLIGHT_UNTIL = new HashMap<>();

    /** Replaces the cached state from a server sync. */
    public static void accept(RulesSyncPayload payload) {
        active = payload.activeIds();
        hud = payload.hud();
        HIGHLIGHT_UNTIL.keySet().retainAll(active);
    }

    /** Flashes one row and clicks, so a toggle reads on camera without any chat. */
    public static void toast(RuleToastPayload payload) {
        HIGHLIGHT_UNTIL.put(payload.ruleId(), System.currentTimeMillis() + HIGHLIGHT_MILLIS);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, payload.enabled() ? 1.4F : 0.8F));
        }
    }

    /** Active rule ids, server order. */
    public static List<String> active() {
        return active;
    }

    /** False while {@code /rule hud off} is hiding the list. */
    public static boolean hudVisible() {
        return hud;
    }

    public static boolean isHighlighted(String ruleId) {
        Long until = HIGHLIGHT_UNTIL.get(ruleId);
        return until != null && System.currentTimeMillis() < until;
    }

    /** Called when the client leaves a world so the next one starts clean. */
    public static void reset() {
        active = List.of();
        hud = true;
        HIGHLIGHT_UNTIL.clear();
    }

    private ClientRuleState() {
    }
}
