package dev.riftal.creator.core.platform;

import com.mojang.serialization.Codec;
import dev.riftal.creator.core.platform.services.IAttachmentHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * NeoForge implementation of {@link IAttachmentHelper}.
 *
 * <p>Attachment types live in a real registry, so {@code create} only builds the type and queues
 * it; {@link #onRegisterAttachments(RegisterEvent)} on the mod bus does the registration.
 */
public final class NeoForgeAttachmentHelper implements IAttachmentHelper {

    private record Pending(ResourceLocation id, AttachmentType<?> type) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    @Override
    public <T> Object create(ResourceLocation id, Codec<T> codec, Supplier<T> defaultValue, boolean copyOnDeath) {
        AttachmentType.Builder<T> builder = AttachmentType.builder(defaultValue::get).serialize(codec);
        if (copyOnDeath) {
            builder.copyOnDeath();
        }
        AttachmentType<T> type = builder.build();
        PENDING.add(new Pending(id, type));
        return type;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object handle, Entity holder) {
        return holder.getData((AttachmentType<T>) handle);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void set(Object handle, Entity holder, T value) {
        holder.setData((AttachmentType<T>) handle, value);
    }

    @Override
    public boolean has(Object handle, Entity holder) {
        return holder.hasData((AttachmentType<?>) handle);
    }

    /** Mod-bus hook wired up by {@code CreatorModsNeoForgeBootstrap}. */
    public static void onRegisterAttachments(RegisterEvent event) {
        if (!NeoForgeRegistries.Keys.ATTACHMENT_TYPES.equals(event.getRegistryKey())) {
            return;
        }
        for (Pending pending : PENDING) {
            event.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, pending.id(), pending::type);
        }
    }
}
