package dev.riftal.creator.core;

import dev.riftal.creator.core.registry.Registrar;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * One shippable feature of the Creator Mods Suite.
 *
 * <p>A feature owns exactly one Java package ({@code dev.riftal.creator.features.<id>}), one
 * resource namespace ({@code creator_<id>}) and one mixin config
 * ({@code creatormods-<id>.mixins.json}). Nothing else. Two features never touch the same file,
 * which is what lets them be written in parallel.
 *
 * <p>Lifecycle, in order, driven by {@link CreatorMods}:
 * <ol>
 *   <li>{@link #registerContent()} - build {@link Registrar}s, {@code PlayerData} attachments,
 *       {@code Payloads} and command trees. Called on both physical sides, once, during mod
 *       construction. Must not touch a live server or any client-only class.</li>
 *   <li>{@link #initCommon()} - wire up anything that does not need the registries to be frozen.
 *       Also called on both sides.</li>
 *   <li>{@link #initClient()} - client only. Safe place for HUD layers, renderers, key mappings.
 *       Never called on a dedicated server, so client-only classes may be referenced here.</li>
 *   <li>{@link #initServer()} - dedicated server only.</li>
 * </ol>
 *
 * <p>A feature that is disabled in {@code config/creatormods.json} gets none of these calls and
 * registers nothing at all.
 */
public interface Feature {

    /** Short, stable, lowercase id. Also the suffix of the mixin config and of the namespace. */
    String id();

    /** Resource namespace owned by this feature: {@code creator_<id>}. */
    default String namespace() {
        return "creator_" + id();
    }

    /** Convenience: {@code creator_<id>:<path>}. */
    default ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace(), path);
    }

    /** Display name, translated from {@code feature.creator_<id>.name} in the feature's lang file. */
    default Component displayName() {
        return Component.translatable("feature." + namespace() + ".name");
    }

    /**
     * Lazy content registration. Returns a {@link Registrar} bound to this feature's namespace;
     * the entries it hands back are suppliers that only resolve once the loader has flushed them
     * into the real registry.
     */
    default <T> Registrar<T> registrar(ResourceKey<Registry<T>> registry) {
        return Registrar.of(registry, namespace());
    }

    /** Declare blocks, items, entity types, sounds, particles, attachments, payloads, commands. */
    default void registerContent() {
    }

    /** Loader-agnostic setup that runs on both physical sides. */
    void initCommon();

    /** Client-only setup. HUD layers, entity renderers and key mappings belong here. */
    default void initClient() {
    }

    /** Dedicated-server-only setup. */
    default void initServer() {
    }
}
