package dev.riftal.creator.core.platform;

import com.mojang.serialization.Codec;
import dev.riftal.creator.core.platform.services.IAttachmentHelper;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.function.Supplier;

/** Fabric implementation of {@link IAttachmentHelper}, over {@code fabric-data-attachment-api-v1}. */
public final class FabricAttachmentHelper implements IAttachmentHelper {

    @Override
    public <T> Object create(ResourceLocation id, Codec<T> codec, Supplier<T> defaultValue, boolean copyOnDeath) {
        return AttachmentRegistry.<T>create(id, builder -> {
            builder.persistent(codec);
            builder.initializer(defaultValue::get);
            if (copyOnDeath) {
                builder.copyOnDeath();
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object handle, Entity holder) {
        return ((AttachmentTarget) holder).getAttachedOrCreate((AttachmentType<T>) handle);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void set(Object handle, Entity holder, T value) {
        ((AttachmentTarget) holder).setAttached((AttachmentType<T>) handle, value);
    }

    @Override
    public boolean has(Object handle, Entity holder) {
        return ((AttachmentTarget) holder).hasAttached((AttachmentType<?>) handle);
    }
}
