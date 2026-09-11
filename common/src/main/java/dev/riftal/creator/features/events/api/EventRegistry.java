package dev.riftal.creator.features.events.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Every event the director can start, by id, in menu order.
 *
 * <p>Populated once from {@code EventsFeature#registerContent()}. Each entry is a factory: a fresh
 * instance is built per start, so an event never carries state from a previous take.
 */
public final class EventRegistry {

    private static final Map<String, Supplier<WorldEvent>> FACTORIES = new LinkedHashMap<>();

    /** Declares one event. Re-registering the same id replaces it, which keeps {@code /reload} sane. */
    public static void register(String id, Supplier<WorldEvent> factory) {
        FACTORIES.put(id, factory);
    }

    /** A fresh instance, or null when {@code id} is unknown. */
    public static WorldEvent create(String id) {
        Supplier<WorldEvent> factory = FACTORIES.get(id);
        return factory == null ? null : factory.get();
    }

    public static boolean contains(String id) {
        return FACTORIES.containsKey(id);
    }

    /** Every registered id, in registration order. */
    public static List<String> ids() {
        return new ArrayList<>(FACTORIES.keySet());
    }

    /** Test support: drops every registration. */
    public static void clear() {
        FACTORIES.clear();
    }

    private EventRegistry() {
    }
}
