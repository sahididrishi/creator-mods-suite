package dev.riftal.creator.features.vault.net;

import dev.riftal.creator.features.vault.block.AltarState;
import net.minecraft.core.BlockPos;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * The most recent {@link VaultStatusPayload} this client has received from each altar.
 *
 * <p>Keyed by altar position rather than kept in one slot. Two altars inside
 * {@code STATUS_BROADCAST_RANGE} is an explicitly supported set-up - the plan's failure-mode table
 * lists it - and a single slot made them overwrite each other every ten ticks, so the readout
 * flickered between two altars. {@link #current(BlockPos)} picks the one nearest the camera
 * instead.
 *
 * <p>Deliberately free of any {@code net.minecraft.client.*} type: the S2C receiver is registered
 * from {@code registerContent()} on both physical sides (see {@code VaultFeature}), so the handler
 * target must be loadable on a dedicated server. Only the HUD layer, which lives in
 * {@code features.vault.client}, reads these entries.
 *
 * <p>Written on the client network thread, read on the render thread, hence the concurrent map.
 */
public final class VaultHudState {

    /** Payload older than this many client ticks is treated as stale and not drawn. */
    public static final int STALE_AFTER_TICKS = 40;

    /** One altar's last known status and when it arrived. */
    private record Entry(VaultStatusPayload payload, long receivedAtMillis) {
    }

    private static final Map<BlockPos, Entry> BY_ALTAR = new ConcurrentHashMap<>();

    /** Client receiver. Registered through {@code Payloads.registerS2C}. */
    public static void accept(VaultStatusPayload payload) {
        BY_ALTAR.put(payload.altarPos(), new Entry(payload, System.currentTimeMillis()));
        pruneStale();
    }

    /**
     * The most recent status from any altar, or {@code null} when nothing fresh has arrived.
     *
     * <p>Ties are broken arbitrarily; {@link #current(BlockPos)} is what the HUD uses.
     */
    @Nullable
    public static VaultStatusPayload current() {
        return current(null);
    }

    /**
     * The freshest status from the altar nearest {@code viewer}, or {@code null} when nothing fresh
     * has arrived.
     *
     * @param viewer where the camera is, or {@code null} to take whatever arrived most recently
     */
    @Nullable
    public static VaultStatusPayload current(@Nullable BlockPos viewer) {
        pruneStale();
        VaultStatusPayload best = null;
        double bestScore = Double.MAX_VALUE;
        long newest = Long.MIN_VALUE;
        for (Entry entry : BY_ALTAR.values()) {
            if (viewer == null) {
                if (entry.receivedAtMillis() > newest) {
                    newest = entry.receivedAtMillis();
                    best = entry.payload();
                }
                continue;
            }
            double distance = entry.payload().altarPos().distSqr(viewer);
            if (distance < bestScore) {
                bestScore = distance;
                best = entry.payload();
            }
        }
        return best;
    }

    /** Convenience for the HUD: the current altar state, or {@code null} when there is nothing. */
    @Nullable
    public static AltarState currentState() {
        VaultStatusPayload payload = current();
        return payload == null ? null : payload.state();
    }

    /** How many altars this client is currently tracking. Fresh entries only. */
    public static int tracked() {
        pruneStale();
        return BY_ALTAR.size();
    }

    /**
     * Drops the cache. Called when the client leaves a world - otherwise the readout from the last
     * world's altar would still be on screen for the first two seconds of the next one - and by
     * tests.
     */
    public static void clear() {
        BY_ALTAR.clear();
    }

    private static void pruneStale() {
        long now = System.currentTimeMillis();
        Iterator<Entry> iterator = BY_ALTAR.values().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().receivedAtMillis() > STALE_AFTER_TICKS * 50L) {
                iterator.remove();
            }
        }
    }

    private VaultHudState() {
    }
}
