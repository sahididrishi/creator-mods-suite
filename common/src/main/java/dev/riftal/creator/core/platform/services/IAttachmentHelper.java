package dev.riftal.creator.core.platform.services;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.function.Supplier;

/**
 * Loader view of entity data attachments (NeoForge {@code AttachmentType} / Fabric
 * {@code AttachmentRegistry}). Features never call this directly - use
 * {@code dev.riftal.creator.core.data.PlayerData}.
 *
 * <p>The handle is opaque on purpose: the two loaders have different attachment classes and common
 * code may not name either of them.
 */
public interface IAttachmentHelper {

    /**
     * Creates (NeoForge: queues for registration on the mod bus) one attachment type.
     *
     * @return an opaque handle to pass back into the accessors
     */
    <T> Object create(ResourceLocation id, Codec<T> codec, Supplier<T> defaultValue, boolean copyOnDeath);

    <T> T get(Object handle, Entity holder);

    <T> void set(Object handle, Entity holder, T value);

    boolean has(Object handle, Entity holder);
}
