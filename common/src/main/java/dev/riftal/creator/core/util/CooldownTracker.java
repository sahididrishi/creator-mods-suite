package dev.riftal.creator.core.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Pure-logic cooldown bookkeeping keyed by anything. No game types, so it unit-tests without a
 * bootstrapped game.
 *
 * <pre>{@code
 * CooldownTracker<String> cooldowns = new CooldownTracker<>();
 * cooldowns.start("dash", level.getGameTime(), 60);
 * if (cooldowns.ready("dash", level.getGameTime())) { ... }
 * }</pre>
 *
 * @param <K> cooldown key, typically an ability id or a {@code UUID}
 */
public final class CooldownTracker<K> {

    private final Map<K, Long> readyAt = new HashMap<>();

    /** Puts {@code key} on cooldown for {@code ticks} ticks starting at {@code now}. */
    public void start(K key, long now, int ticks) {
        readyAt.put(key, now + Math.max(0, ticks));
    }

    /** True when the cooldown has elapsed (or was never started). */
    public boolean ready(K key, long now) {
        Long until = readyAt.get(key);
        return until == null || now >= until;
    }

    /** Ticks left, 0 when ready. */
    public int remaining(K key, long now) {
        Long until = readyAt.get(key);
        if (until == null || now >= until) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, until - now);
    }

    /** Fraction of the cooldown still to run, 0..1, for HUD bars. */
    public float progress(K key, long now, int fullLength) {
        return fullLength <= 0 ? 0.0F : remaining(key, now) / (float) fullLength;
    }

    public void clear(K key) {
        readyAt.remove(key);
    }

    public void clearAll() {
        readyAt.clear();
    }

    public boolean isEmpty() {
        return readyAt.isEmpty();
    }
}
