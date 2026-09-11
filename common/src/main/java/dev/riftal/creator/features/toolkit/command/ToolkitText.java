package dev.riftal.creator.features.toolkit.command;

import dev.riftal.creator.features.toolkit.ToolkitFeature;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Builds the feature's translatable feedback. Every key here has a line in the lang file. */
public final class ToolkitText {

    private static final String PREFIX = "commands." + ToolkitFeature.NAMESPACE + ".";

    /** {@code commands.creator_toolkit.<suffix>} with arguments. */
    public static MutableComponent of(String suffix, Object... args) {
        return Component.translatable(PREFIX + suffix, args);
    }

    /** Localised {@code ON} / {@code OFF}, for toggle feedback. */
    public static Component onOff(boolean value) {
        return Component.translatable(PREFIX + (value ? "on" : "off"));
    }

    private ToolkitText() {
    }
}
