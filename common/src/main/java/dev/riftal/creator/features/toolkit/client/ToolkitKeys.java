package dev.riftal.creator.features.toolkit.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import net.minecraft.client.KeyMapping;

import java.util.List;

/**
 * The mark key. <strong>Client only.</strong>
 *
 * <p>Tapping it in game sends {@code mark_pressed} to the server - the same thing
 * {@code /toolkit take mark} does, except no chat box ever opens, which is the whole point: on
 * camera the director's hand moves and the HUD flashes, and nothing else happens.
 *
 * <p>It is a real {@link KeyMapping}, not a raw key-code check in a {@code KeyboardHandler} mixin.
 * That matters for one specific reason the plan calls out: the default is {@code M}, and Xaero's
 * Minimap and JourneyMap both bind {@code M} as well. A {@code KeyMapping} appears in
 * Options &gt; Controls, shows the conflict, is saved to {@code options.txt} and can be rebound; a
 * hardcoded key check cannot, and would fire our mark every time the director opened their map.
 *
 * <p>Constructing the mapping is enough for {@code consumeClick()} to work - the vanilla
 * constructor registers it in {@code KeyMapping.MAP}. Appearing in the controls screen additionally
 * needs it in {@code Options#keyMappings}, and that is loader territory: see the two glue classes
 * named in {@link ToolkitClient}.
 *
 * <p>Polled with {@code consumeClick()} in a {@code while} loop, never {@code isDown()}: a held key
 * must fire once, and presses buffered by a lag spike must each be honoured exactly once.
 */
public final class ToolkitKeys {

    /** Options &gt; Controls category, translated from the feature's lang file. */
    public static final String CATEGORY = "key.categories." + ToolkitFeature.NAMESPACE;

    /** Translation key of the mark binding. */
    public static final String MARK = "key." + ToolkitFeature.NAMESPACE + ".mark";

    private static KeyMapping mark;

    /** The mark mapping, created on first call. Idempotent. */
    public static synchronized KeyMapping markKey() {
        if (mark == null) {
            mark = new KeyMapping(MARK, InputConstants.Type.KEYSYM, InputConstants.KEY_M, CATEGORY);
        }
        return mark;
    }

    /** Every mapping this feature owns, for the loader glue that registers them with the options screen. */
    public static List<KeyMapping> list() {
        return List.of(markKey());
    }

    /** Drains the mark key's click queue. Called once per client tick from the loader glue. */
    public static void poll() {
        KeyMapping key = markKey();
        while (key.consumeClick()) {
            ClientToolkitState.sendMark();
        }
    }

    private ToolkitKeys() {
    }
}
