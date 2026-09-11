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

    /** How long the white flash at the end of a transformation lasts. */
    public static final long FLASH_MILLIS = 500L;

    /** Most pop-ups kept at once; a kill streak should not fill the screen. */
    private static final int MAX_POPUPS = 6;

    /** One running transformation, as the client sees it. */
    public record Transform(long startMillis, int durationTicks, int targetStage) {

        public long endMillis() {
            return startMillis + durationTicks * 50L;
        }

        /** 0..1 across the whole sequence. */
        public float progress(long now) {
            long span = Math.max(1L, endMillis() - startMillis);
            float raw = (float) (now - startMillis) / (float) span;
            return raw < 0.0F ? 0.0F : Math.min(raw, 1.0F);
        }

        /** 0..1 flash strength: ramps up then down over the last {@link #FLASH_MILLIS}. */
        public float flash(long now) {
            long remaining = endMillis() - now;
            if (remaining > FLASH_MILLIS || remaining < -FLASH_MILLIS / 2L) {
                return 0.0F;
            }
            float t = 1.0F - (float) remaining / (float) FLASH_MILLIS;
            float clamped = t < 0.0F ? 0.0F : Math.min(t, 2.0F);
            return clamped <= 1.0F ? clamped : Math.max(0.0F, 2.0F - clamped);
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
            TRANSFORMS.remove(playerId);
        }
    }

    /** Starts the screen effect for one player. */
    public static void startTransform(UUID playerId, int durationTicks, int targetStage) {
        TRANSFORMS.put(playerId, new Transform(System.currentTimeMillis(),
                Math.max(1, durationTicks), targetStage));
    }

    /** Ends the screen effect for one player. */
    public static void stopTransform(UUID playerId) {
        TRANSFORMS.remove(playerId);
    }

    /** The running transformation for one player, or null. */
    public static Transform transform(UUID playerId) {
        return TRANSFORMS.get(playerId);
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
        POPUPS.clear();
    }

    private ClientEvolutionCache() {
    }
}
