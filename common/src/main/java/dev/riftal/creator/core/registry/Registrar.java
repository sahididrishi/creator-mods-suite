package dev.riftal.creator.core.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Loader-neutral deferred registration. Works for every vanilla registry: blocks, items, entity
 * types, block entity types, sound events, particle types, mob effects, creative tabs, recipe
 * serializers, and so on.
 *
 * <pre>{@code
 * private final Registrar<Item> items = registrar(Registries.ITEM);
 * public final RegistryEntry<Item> hammer = items.register("hammer", () -> new Item(new Item.Properties()));
 * }</pre>
 *
 * <p>Nothing is created when you call {@link #register}; the suppliers run when the loader flushes
 * (Fabric: right after mod init; NeoForge: inside {@code RegisterEvent}). Read values through
 * {@link RegistryEntry#get()} at use time.
 *
 * @param <T> the registry's element type
 */
public final class Registrar<T> {

    private static final List<Registrar<?>> ALL = new ArrayList<>();

    private final ResourceKey<Registry<T>> registryKey;
    private final String namespace;
    private final List<RegistryEntry<? extends T>> entries = new ArrayList<>();

    private Registrar(ResourceKey<Registry<T>> registryKey, String namespace) {
        this.registryKey = registryKey;
        this.namespace = namespace;
    }

    /**
     * Creates (or reuses) the registrar for one registry in one namespace. Prefer
     * {@code Feature#registrar(ResourceKey)} which fills the namespace in for you.
     */
    public static synchronized <T> Registrar<T> of(ResourceKey<Registry<T>> registryKey, String namespace) {
        for (Registrar<?> existing : ALL) {
            if (existing.registryKey.equals(registryKey) && existing.namespace.equals(namespace)) {
                @SuppressWarnings("unchecked")
                Registrar<T> cast = (Registrar<T>) existing;
                return cast;
            }
        }
        Registrar<T> created = new Registrar<>(registryKey, namespace);
        ALL.add(created);
        return created;
    }

    /**
     * Queues one object for registration at {@code <namespace>:<path>}.
     *
     * @param path  registry path: lowercase, {@code [a-z0-9_/.-]}
     * @param value factory, run once at flush time
     */
    public <R extends T> RegistryEntry<R> register(String path, Supplier<R> value) {
        RegistryEntry<R> entry =
                new RegistryEntry<>(ResourceLocation.fromNamespaceAndPath(namespace, path), value);
        entries.add(entry);
        return entry;
    }

    public ResourceKey<Registry<T>> registryKey() {
        return registryKey;
    }

    public String namespace() {
        return namespace;
    }

    public List<RegistryEntry<? extends T>> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** Every registrar declared so far, in declaration order. Loader glue only. */
    public static synchronized List<Registrar<?>> all() {
        return List.copyOf(ALL);
    }

    /** Pushes every queued entry of every registrar into a loader-provided sink. Loader glue only. */
    public static void flushAll(Sink sink) {
        for (Registrar<?> registrar : all()) {
            registrar.flush(sink);
        }
    }

    /** Pushes this registrar's queued entries into a sink. Loader glue only. */
    public void flush(Sink sink) {
        for (RegistryEntry<? extends T> entry : entries) {
            sink.accept(registryKey, entry.id(), entry::createAndBind);
        }
    }

    /** Implemented by each loader: the real {@code Registry.register} / {@code RegisterEvent} call. */
    @FunctionalInterface
    public interface Sink {
        <T> void accept(ResourceKey<Registry<T>> registryKey, ResourceLocation id, Supplier<? extends T> value);
    }
}
