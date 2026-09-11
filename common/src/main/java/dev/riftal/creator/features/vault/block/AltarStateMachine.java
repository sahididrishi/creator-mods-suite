package dev.riftal.creator.features.vault.block;

/**
 * The Cursed Altar's transition table and its timings, with no Minecraft types anywhere so it can
 * be unit-tested without bootstrapping the game.
 *
 * <pre>
 * SEALED   --KEY--------------&gt; CHARGING
 * CHARGING --CHARGE_COMPLETE--&gt; ACTIVE      (after {@link #CHARGE_TICKS} ticks)
 * ACTIVE   --KEEPER_DEAD------&gt; SPENT
 * *        --RESET------------&gt; SEALED
 * </pre>
 */
public final class AltarStateMachine {

    /** Ticks between accepting the key and the Keeper bursting out of the floor. */
    public static final int CHARGE_TICKS = 60;

    /** How often the altar re-checks that its bound Keeper still exists, in ticks. */
    public static final int KEEPER_CHECK_INTERVAL = 20;

    /**
     * A bound Keeper that cannot be resolved for this many consecutive ticks counts as dead. Long
     * enough to survive the chunk the Keeper stands in being unloaded for a moment.
     */
    public static final int KEEPER_MISSING_LIMIT = 100;

    /** Radius, in blocks, of the Sealed Chest scan performed when the altar is activated. */
    public static final int CHEST_SCAN_RADIUS = 16;

    /** How often an active altar pushes its status to nearby players, in ticks. */
    public static final int STATUS_BROADCAST_INTERVAL = 10;

    /** Radius, in blocks, within which players receive the altar's status payload. */
    public static final double STATUS_BROADCAST_RANGE = 32.0D;

    /** Things that can happen to an altar. */
    public enum Event {
        /** A Vault Key was offered. */
        KEY,
        /** The charge countdown ran out. */
        CHARGE_COMPLETE,
        /** The bound Keeper died or vanished. */
        KEEPER_DEAD,
        /** {@code /vault reset}. */
        RESET
    }

    /**
     * The state an altar moves to. Returns {@code from} unchanged when the event does not apply,
     * which is what makes "key while charging" a no-op rather than a second summon.
     */
    public static AltarState next(AltarState from, Event event) {
        if (event == Event.RESET) {
            return AltarState.SEALED;
        }
        return switch (from) {
            case SEALED -> event == Event.KEY ? AltarState.CHARGING : from;
            case CHARGING -> event == Event.CHARGE_COMPLETE ? AltarState.ACTIVE : from;
            case ACTIVE -> event == Event.KEEPER_DEAD ? AltarState.SPENT : from;
            case SPENT -> from;
        };
    }

    /** True when offering a key to an altar in this state should consume the key. */
    public static boolean acceptsKey(AltarState state) {
        return state == AltarState.SEALED;
    }

    /** 0..1 charge progress, for the HUD bar. Always 0 outside {@link AltarState#CHARGING}. */
    public static float chargeProgress(AltarState state, int chargeTicks) {
        if (state != AltarState.CHARGING) {
            return 0.0F;
        }
        if (chargeTicks <= 0) {
            return 0.0F;
        }
        if (chargeTicks >= CHARGE_TICKS) {
            return 1.0F;
        }
        return (float) chargeTicks / (float) CHARGE_TICKS;
    }

    private AltarStateMachine() {
    }
}
