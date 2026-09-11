package dev.riftal.creator.core.data;

import com.mojang.serialization.Codec;
import dev.riftal.creator.core.platform.CoreServices;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Per-entity persistent data, one abstraction over NeoForge {@code AttachmentType} and Fabric
 * {@code AttachmentRegistry}.
 *
 * <pre>{@code
 * public static final PlayerData<Integer> CHARGE = PlayerData.register(
 *         ResourceLocation.fromNamespaceAndPath("creator_powers", "charge"),
 *         Codec.INT, () -> 0, true);
 *
 * int charge = CHARGE.get(player);
 * CHARGE.update(player, c -> c + 1);
 * }</pre>
 *
 * <p>Register from {@code Feature#registerContent()} - on NeoForge the type is queued and flushed
 * inside {@code RegisterEvent} on the mod bus, so registering later silently misses the window.
 *
 * <p>Nothing here syncs to the client. Attachments are server-side state; mirror what the client
 * needs with {@code dev.riftal.creator.core.net.Payloads} and validate on the server.
 *
 * @param <T> the stored value type
 */
public final class PlayerData<T> {

    private final ResourceLocation id;
    private final Object handle;

    private PlayerData(ResourceLocation id, Object handle) {
        this.id = id;
        this.handle = handle;
    }

    /**
     * Declares one attachment.
     *
     * @param id           namespaced with your feature's namespace, e.g. {@code creator_powers:charge}
     * @param codec        used for disk persistence
     * @param defaultValue supplier for entities that have no value yet; must never return null
     * @param copyOnDeath  keep the value across a respawn
     */
    public static <T> PlayerData<T> register(ResourceLocation id, Codec<T> codec,
                                             Supplier<T> defaultValue, boolean copyOnDeath) {
        return new PlayerData<>(id, CoreServices.ATTACHMENTS.create(id, codec, defaultValue, copyOnDeath));
    }

    public ResourceLocation id() {
        return id;
    }

    /** Current value, creating the default if the entity has none yet. */
    public T get(Entity holder) {
        return CoreServices.ATTACHMENTS.get(handle, holder);
    }

    /** Overwrites the value. */
    public void set(Entity holder, T value) {
        CoreServices.ATTACHMENTS.set(handle, holder, value);
    }

    /** Read-modify-write. Returns the new value. */
    public T update(Entity holder, UnaryOperator<T> mutator) {
        T updated = mutator.apply(get(holder));
        set(holder, updated);
        return updated;
    }

    /** True if a value has been stored (i.e. without creating the default). */
    public boolean has(Entity holder) {
        return CoreServices.ATTACHMENTS.has(handle, holder);
    }

    @Override
    public String toString() {
        return "PlayerData[" + id + "]";
    }
}
