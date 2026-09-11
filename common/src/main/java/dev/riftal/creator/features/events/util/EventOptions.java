package dev.riftal.creator.features.events.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code key=value key=value} tail of {@code /event start <name> [options]}.
 *
 * <p>Pure logic, no Minecraft types: unknown keys are kept but ignored, malformed numbers fall back
 * to the caller's default, and the raw text round-trips through the event's saved data so a resumed
 * event keeps the options it was started with.
 */
public final class EventOptions {

    private static final EventOptions EMPTY = new EventOptions("", Map.of());

    private final String raw;
    private final Map<String, String> values;

    private EventOptions(String raw, Map<String, String> values) {
        this.raw = raw;
        this.values = values;
    }

    /** No options at all. */
    public static EventOptions empty() {
        return EMPTY;
    }

    /**
     * Parses {@code "maxY=60 speed=0.1 bossbar=false"}. Whitespace separated, {@code key=value},
     * keys lowercased. A bare word with no {@code =} is stored with an empty value so
     * {@link #has(String)} still sees it.
     */
    public static EventOptions parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String token : raw.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            int eq = token.indexOf('=');
            if (eq < 0) {
                parsed.put(token.toLowerCase(Locale.ROOT), "");
            } else if (eq > 0) {
                parsed.put(token.substring(0, eq).toLowerCase(Locale.ROOT), token.substring(eq + 1));
            }
        }
        return new EventOptions(raw.trim(), Collections.unmodifiableMap(parsed));
    }

    /** The text the director typed, for {@code /event status} and for saved data. */
    public String raw() {
        return raw;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public boolean has(String key) {
        return values.containsKey(key.toLowerCase(Locale.ROOT));
    }

    public double getDouble(String key, double fallback) {
        String value = values.get(key.toLowerCase(Locale.ROOT));
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public double getDouble(String key, double fallback, double min, double max) {
        double value = getDouble(key, fallback);
        return value < min ? min : Math.min(value, max);
    }

    public int getInt(String key, int fallback) {
        String value = values.get(key.toLowerCase(Locale.ROOT));
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public int getInt(String key, int fallback, int min, int max) {
        int value = getInt(key, fallback);
        return value < min ? min : Math.min(value, max);
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = values.get(key.toLowerCase(Locale.ROOT));
        if (value == null) {
            return fallback;
        }
        if (value.isEmpty()) {
            return true;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    public String getString(String key, String fallback) {
        String value = values.get(key.toLowerCase(Locale.ROOT));
        return value == null || value.isEmpty() ? fallback : value;
    }

    @Override
    public String toString() {
        return raw.isEmpty() ? "(no options)" : raw;
    }
}
