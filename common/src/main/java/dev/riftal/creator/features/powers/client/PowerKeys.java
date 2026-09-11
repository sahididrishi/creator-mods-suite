package dev.riftal.creator.features.powers.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.util.List;

/**
 * The six ability keys, slot 1 to slot 6, defaulting to R F G V C X.
 *
 * <p><b>Client only.</b>
 *
 * <p>Constructing a {@link KeyMapping} is enough to make {@code consumeClick()} work: the vanilla
 * constructor puts the mapping into {@code KeyMapping.MAP}, which is what the keyboard handler
 * looks presses up in. Appearing in Options -> Controls and being saved to {@code options.txt}
 * additionally needs the mapping to be in {@code Options#keyMappings}, and that array is loader
 * territory - see the two glue classes named in {@code PowersClient}.
 *
 * <p>Polled with {@code consumeClick()} in a {@code while} loop, never {@code isDown()}: a held key
 * must fire once, and buffered presses from a lag spike must all be honoured exactly once.
 */
public final class PowerKeys {

    /** Options -> Controls category, translated from the feature's lang file. */
    public static final String CATEGORY = "key.categories.creator_powers";

    private static final int[] DEFAULT_KEYS = {
            InputConstants.KEY_R,
            InputConstants.KEY_F,
            InputConstants.KEY_G,
            InputConstants.KEY_V,
            InputConstants.KEY_C,
            InputConstants.KEY_X,
    };

    private static KeyMapping[] mappings;

    /** The six mappings, created on first call. Idempotent. */
    public static synchronized KeyMapping[] all() {
        if (mappings == null) {
            mappings = new KeyMapping[DEFAULT_KEYS.length];
            for (int i = 0; i < DEFAULT_KEYS.length; i++) {
                mappings[i] = new KeyMapping("key.creator_powers.slot" + (i + 1),
                        InputConstants.Type.KEYSYM, DEFAULT_KEYS[i], CATEGORY);
            }
        }
        return mappings;
    }

    /** The six mappings as a list, for the loader glue that registers them with the options screen. */
    public static List<KeyMapping> list() {
        return List.of(all());
    }

    /** The mapping bound to one slot, or null for an out-of-range slot. */
    public static KeyMapping forSlot(int slot) {
        KeyMapping[] keys = all();
        return slot < 0 || slot >= keys.length ? null : keys[slot];
    }

    /**
     * Drains the click queue of every slot key and fires the matching ability. Called once per
     * client tick from {@code PowersClient#clientTick(boolean)}, which does the HUD mirror's
     * housekeeping first.
     */
    public static void poll() {
        KeyMapping[] keys = all();
        for (int slot = 0; slot < keys.length; slot++) {
            while (keys[slot].consumeClick()) {
                ClientPowers.pressSlot(slot);
            }
        }
    }

    private PowerKeys() {
    }
}
