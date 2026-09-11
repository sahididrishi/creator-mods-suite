package dev.riftal.creator.features.vault.net;

import dev.riftal.creator.features.vault.block.AltarState;

/**
 * The last {@link VaultStatusPayload} this client received, plus the time it arrived.
 *
 * <p>Deliberately free of any {@code net.minecraft.client.*} type: the S2C receiver is registered
 * from {@code registerContent()} on both physical sides (see {@code VaultFeature}), so the handler
 * target must be loadable on a dedicated server. Only the HUD layer, which lives in
 * {@code features.vault.client}, reads these fields.
 *
 * <p>Written on the client network thread, read on the render thread, hence {@code volatile}.
 */
public final class VaultHudState {

    /** Payload older than this many client ticks is treated as stale and not drawn. */
    public static final int STALE_AFTER_TICKS = 40;

    private static volatile VaultStatusPayload latest;
    private static volatile long receivedAtMillis;

    /** Client receiver. Registered through {@code Payloads.registerS2C}. */
    public static void accept(VaultStatusPayload payload) {
        latest = payload;
        receivedAtMillis = System.currentTimeMillis();
    }

    /** The most recent status, or {@code null} when nothing has arrived or it went stale. */
    public static VaultStatusPayload current() {
        VaultStatusPayload payload = latest;
        if (payload == null) {
            return null;
        }
        long ageMillis = System.currentTimeMillis() - receivedAtMillis;
        if (ageMillis > STALE_AFTER_TICKS * 50L) {
            return null;
        }
        return payload;
    }

    /** Convenience for the HUD: the current altar state, or {@code null} when there is nothing. */
    public static AltarState currentState() {
        VaultStatusPayload payload = current();
        return payload == null ? null : payload.state();
    }

    /** Drops the cache - used when the player disconnects and by tests. */
    public static void clear() {
        latest = null;
        receivedAtMillis = 0L;
    }

    private VaultHudState() {
    }
}
