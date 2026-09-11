package dev.riftal.creator.core.registry;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A lazy handle on one registered object. Returned by {@link Registrar#register}.
 *
 * <p>The value does not exist until the loader flushes the registrar, which happens after your
 * {@code registerContent()} returns. Hold on to the entry, call {@link #get()} at use time - never
 * at class-initialisation time.
 *
 * @param <T> the registered type
 */
public final class RegistryEntry<T> implements Supplier<T> {

    private final ResourceLocation id;
    private final Supplier<T> factory;
    private T value;

    RegistryEntry(ResourceLocation id, Supplier<T> factory) {
        this.id = Objects.requireNonNull(id, "id");
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    /** The id this object is registered under. Always available, even before the flush. */
    public ResourceLocation id() {
        return id;
    }

    /** True once the loader has created and registered the value. */
    public boolean isBound() {
        return value != null;
    }

    @Override
    public T get() {
        if (value == null) {
            throw new IllegalStateException(
                    "Registry entry " + id + " was read before it was registered. Read it lazily "
                            + "(inside a method body), not in a static initialiser.");
        }
        return value;
    }

    /** Called by the loader flush: creates the object exactly once and binds it. */
    T createAndBind() {
        if (value == null) {
            value = Objects.requireNonNull(factory.get(), () -> "factory for " + id + " returned null");
        }
        return value;
    }

    @Override
    public String toString() {
        return "RegistryEntry[" + id + (value == null ? ", unbound]" : "]");
    }
}
