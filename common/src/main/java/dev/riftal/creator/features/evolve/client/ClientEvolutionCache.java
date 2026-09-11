package dev.riftal.creator.features.evolve.client;

import dev.riftal.creator.features.evolve.data.EvolutionData;
import dev.riftal.creator.features.evolve.net.XpPopupPayload;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * What the client knows about everybody's evolution, filled in by
 * {@code SyncEvolutionPayload}/{@code TransformFxPayload}/{@code XpPopupPayload}.
 *
 * <p>Pure data - no {@code net.minecraft.client} types - but only ever touched from client code.
 * Animations are timed off the wall clock rather than a tick counter because core exposes no
 * client-tick hook; 50 ms per tick is close enough for a HUD.
 */
public final class ClientEvolutionCache {

    /** How long a "+15 EVO" pop-up stays on screen. */
    public static final long POPUP_MILLIS = 1_500L;

    /** How long the white flash at the end of a transformation lasts on the way up. */
    public static final long FLASH_MILLIS = 500L;

    /** How long one shot of the beast's roar animation runs. */
    public static final long ROAR_MILLIS = 1_500L;

    /** Most pop-ups kept at once; a kill streak should not fill the screen. */
    private static final int MAX_POPUPS = 6;

    /**
     * One transformation, as the client sees it.
     *
     * @param endedAtMillis 0 while the sequence is running; the wall clock of the STOP packet after
     *                      that. The entry deliberately outlives the STOP - see {@link #flash}.
     */
    public record Transform(long startMillis, int durationTicks, int targetStage, long endedAtMillis) {

        public long endMillis() {
            return startMillis + durationTicks * 50L;
        }

        /** True until the server's STOP packet lands. */
        public boolean running() {
            return endedAtMillis == 0L;
        }

        /** A copy marked as stopped at {@code now}. */
        public Transform ended(long now) {
            return running() ? new Transform(startMillis, durationTicks, targetStage, now) : this;
        }

        /** 0..1 across the whole sequence. */
        public float progress(long now) {
            long span = Math.max(1L, endMillis() - startMillis);
            float raw = (float) (now - startMillis) / (float) span;
            return raw < 0.0F ? 0.0F : Math.min(raw, 1.0F);
        }

        /**
         * 0..1 flash strength: ramps up over the last {@link #FLASH_MILLIS} of the sequence and back
         * down over the {@code FLASH_MILLIS / 2} after it ends, exactly as the plan's
         * "alpha 0 -&gt; 0.9 -&gt; 0" asks.
         *
         * <p>The down ramp is the whole reason this record survives its own STOP packet. The server
         * sends STOP and the sync at the same instant the flash peaks, and both used to delete the
         * entry - so the screen cut from alpha 230 to nothing in a single frame and the plan's
         * symmetric flash never once rendered.
         */
        public float flash(long now) {
            long remaining = endMillis() - now;
            if (remaining > FLASH_MILLIS || remaining < -FLASH_MILLIS / 2L) {
                return 0.0F;
            }
            float t = 1.0F - (float) remaining / (float) FLASH_MILLIS;
            float clamped = t < 0.0F ? 0.0F : Math.min(t, 2.0F);
            return clamped <= 1.0F ? clamped : Math.max(0.0F, 2.0F - clamped);
        }

        /**
         * True once this entry has nothing left to draw and can be dropped: one fade-length after
         * the STOP landed. A sequence that ran to completion spends that window drawing the down
         * ramp; one that was aborted early spends it drawing nothing (the ramp only exists in the
         * last {@link #FLASH_MILLIS} of the sequence, which an abort never reaches) and is simply
         * tidied away - no phantom flash at the time the transformation would have ended.
         */
        public boolean expired(long now) {
            return !running() && now >= endedAtMillis + FLASH_MILLIS / 2L;
        }
    }

    /** One floating "+N EVO" label. */
    public record Popup(int amount, int source, long shownAtMillis) {

        public float age(long now) {
            float raw = (float) (now - shownAtMillis) / (float) POPUP_MILLIS;
            return raw < 0.0F ? 0.0F : Math.min(raw, 1.0F);
        }
    }

    private static final Map<UUID, EvolutionData> STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Transform> TRANSFORMS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ROARS = new ConcurrentHashMap<>();
    private static final Deque<Popup> POPUPS = new ConcurrentLinkedDeque<>();

    /** Identity of the client level the cache belongs to. Never dereferenced. */
    private static volatile Object lastLevel;

    /** State for one player; {@link EvolutionData#INITIAL} if we have not heard about them. */
    public static EvolutionData state(UUID playerId) {
        EvolutionData known = STATES.get(playerId);
        return known == null ? EvolutionData.INITIAL : known;
    }

    /** True when a sync for this player has actually arrived. */
    public static boolean knows(UUID playerId) {
        return STATES.containsKey(playerId);
    }

    /** Stores one player's synced state. */
    public static void put(UUID playerId, int stage, int xp, boolean transforming, int modelOverride) {
        STATES.put(playerId, new EvolutionData(stage, xp, 0, transforming, 0L, modelOverride));
        if (!transforming) {
            markEnded(playerId);
        }
    }

    /** Starts the screen effect for one player. */
    public static void startTransform(UUID playerId, int durationTicks, int targetStage) {
        TRANSFORMS.put(playerId, new Transform(System.currentTimeMillis(),
                Math.max(1, durationTicks), targetStage, 0L));
    }

    /**
     * Ends the screen effect for one player. The entry is <em>marked</em> rather than deleted so the
     * flash can finish fading; {@link #transform} drops it once it has nothing left to draw.
     */
    public static void stopTransform(UUID playerId) {
        markEnded(playerId);
    }

    /** The transformation for one player, or null. Expired entries are dropped as a side effect. */
    public static Transform transform(UUID playerId) {
        Transform current = TRANSFORMS.get(playerId);
        if (current == null) {
            return null;
        }
        if (current.expired(System.currentTimeMillis())) {
            TRANSFORMS.remove(playerId, current);
            return null;
        }
        return current;
    }

    /** Every transformation the client currently knows about. Expired entries are dropped. */
    public static Map<UUID, Transform> transforms() {
        long now = System.currentTimeMillis();
        TRANSFORMS.entrySet().removeIf(entry -> entry.getValue().expired(now));
        return TRANSFORMS;
    }

    /** Plays one shot of the beast's roar animation for this player. */
    public static void startRoar(UUID playerId) {
        ROARS.put(playerId, System.currentTimeMillis() + ROAR_MILLIS);
    }

    /** True while this player's beast should be roaring. Expired entries are dropped. */
    public static boolean roaring(UUID playerId) {
        Long until = ROARS.get(playerId);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            ROARS.remove(playerId, until);
            return false;
        }
        return true;
    }

    /** Queues a "+N EVO" pop-up for the local player. */
    public static void addPopup(int amount, int source) {
        POPUPS.addLast(new Popup(amount, source, System.currentTimeMillis()));
        while (POPUPS.size() > MAX_POPUPS) {
            POPUPS.pollFirst();
        }
    }

    /** Live pop-ups, oldest first. Expired entries are dropped as a side effect. */
    public static List<Popup> popups() {
        long now = System.currentTimeMillis();
        Iterator<Popup> it = POPUPS.iterator();
        while (it.hasNext()) {
            if (now - it.next().shownAtMillis() > POPUP_MILLIS) {
                it.remove();
            }
        }
        return new ArrayList<>(POPUPS);
    }

    /** Colour for a pop-up, by {@code XpPopupPayload.SOURCE_*}. */
    public static int popupColour(int source) {
        return switch (source) {
            case XpPopupPayload.SOURCE_FOOD -> 0xFF8FD96A;
            case XpPopupPayload.SOURCE_COMMAND -> 0xFF8FB4E8;
            default -> 0xFFE8D06A;
        };
    }

    /**
     * Drops everything if the client has moved to a different level instance - leaving a world and
     * opening another would otherwise leave the previous world's stages in the cache for the second
     * or so before the server's first sync arrives. Takes {@code Object} so this class stays free of
     * {@code net.minecraft.client} types.
     *
     * <p>Called from the HUD layer <em>before</em> its F1 guard: the render swap reads the same
     * cache, so which world's stages are live must not depend on whether the HUD is visible.
     */
    public static void onLevel(Object level) {
        if (level != lastLevel) {
            lastLevel = level;
            clear();
        }
    }

    /** Wipes everything. Called on a level change and when the client disconnects. */
    public static void clear() {
        STATES.clear();
        TRANSFORMS.clear();
        ROARS.clear();
        POPUPS.clear();
    }

    private static void markEnded(UUID playerId) {
        long now = System.currentTimeMillis();
        TRANSFORMS.computeIfPresent(playerId,
                (id, current) -> current.expired(now) ? null : current.ended(now));
    }

    private ClientEvolutionCache() {
    }
}
