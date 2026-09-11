package dev.riftal.creator.features.powers.ability;

import dev.riftal.creator.features.powers.PowersFeature;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * One keybind superpower.
 *
 * <p>Abilities are plain server-side objects, not registry entries: there are exactly six, they are
 * never data-driven, and nothing in the game world needs to look one up by numeric id. They are
 * held by {@link AbilityRegistry} in slot order.
 *
 * <p>Implementations must not touch any {@code net.minecraft.client} type - an ability runs on the
 * logical server, including inside a dedicated server.
 */
public interface Ability {

    /** Registry-style path, e.g. {@code dash}. Also the lang-key suffix. */
    String path();

    /** Full id, {@code creator_powers:<path>}. */
    ResourceLocation id();

    /** Cooldown in ticks, started the moment the ability fires. */
    int cooldownTicks();

    /** Cheap pre-flight check. Runs on the server before anything is spent. */
    default boolean canUse(ServerPlayer player) {
        return true;
    }

    /** Do the thing. Only ever called after {@link #canUse(ServerPlayer)} returned true. */
    void activate(AbilityContext context);

    /** Translated display name, from {@code ability.creator_powers.<path>}. */
    default Component displayName() {
        return Component.translatable("ability.creator_powers." + path());
    }

    /**
     * ARGB tint of the HUD slot. Plain data, so it lives here rather than in the client package;
     * the client never has to know about the concrete ability classes.
     */
    int hudColor();

    /**
     * The 16x16 sprite drawn in this ability's HUD slot,
     * {@code creator_powers:textures/gui/abilities/<path>.png}.
     *
     * <p>A {@code ResourceLocation} is plain data, so naming it here rather than in the client
     * package keeps the HUD ignorant of the concrete ability classes. Nothing on a dedicated server
     * ever loads the file.
     */
    default ResourceLocation icon() {
        return PowersFeature.guiTexture("abilities/" + path());
    }
}
