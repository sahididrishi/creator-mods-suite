package dev.riftal.creator.features.toolkit.client;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.toolkit.net.FreezeStatePayload;
import dev.riftal.creator.features.toolkit.net.HideStatePayload;
import dev.riftal.creator.features.toolkit.net.MarkPressedPayload;
import dev.riftal.creator.features.toolkit.net.RequestSyncPayload;
import dev.riftal.creator.features.toolkit.net.TakeStatePayload;
import net.minecraft.client.Minecraft;

/**
 * The client's mirror of the toolkit. <strong>Client only</strong> - reachable from
 * {@code initClient()}, from the HUD layer and from the three client mixins, and from nowhere else.
 *
 * <p>The take timer runs off the client's own wall clock so it is smooth between packets; the
 * server's clock is folded in once per update so a LAN host with a skewed clock cannot make the
 * timer jump.
 */
public final class ClientToolkitState {

    /** How long a {@code MARK n} flash stays on screen, in milliseconds. */
    private static final long MARK_FLASH_MS = 2_000L;

    private static int takeNumber = 1;
    private static boolean running;
    private static long startEpochMs;
    private static long stoppedRtaMs;
    private static int markCount;

    private static long markFlashUntilMs;
    private static int flashedMark;

    private static boolean mobsFrozen;
    private static boolean playersFrozen;

    private static boolean hideHud;
    private static boolean hideChat;
    private static boolean hideNametags;

    private static Object syncedLevel;

    // ----- state the HUD asks for ------------------------------------------------------------

    public static int takeNumber() {
        return takeNumber;
    }

    public static boolean running() {
        return running;
    }

    public static int markCount() {
        return markCount;
    }

    public static boolean mobsFrozen() {
        return mobsFrozen;
    }

    public static boolean playersFrozen() {
        return playersFrozen;
    }

    /** Elapsed real time of the take: live while running, frozen once stopped. */
    public static long rtaMillis() {
        if (!running) {
            return stoppedRtaMs;
        }
        return Math.max(0L, System.currentTimeMillis() - startEpochMs);
    }

    /** The mark number to flash right now, or 0 when the flash has expired. */
    public static int markFlash() {
        return System.currentTimeMillis() < markFlashUntilMs ? flashedMark : 0;
    }

    // ----- flags the mixins ask for ----------------------------------------------------------

    /** True while {@code /toolkit hide chat on} is in force. */
    public static boolean chatHidden() {
        return hideChat;
    }

    /** True while {@code /toolkit hide nametags on} is in force. */
    public static boolean nametagsHidden() {
        return hideNametags;
    }

    /** True while {@code /toolkit hide hud on} is in force. */
    public static boolean hudHiddenByToolkit() {
        return hideHud;
    }

    // ----- payload receivers -----------------------------------------------------------------

    /** S2C {@code take_state}. Runs on the client thread. */
    public static void onTakeState(TakeStatePayload payload) {
        long skew = System.currentTimeMillis() - payload.serverEpochMs();
        boolean newTake = payload.takeNumber() != takeNumber;
        takeNumber = payload.takeNumber();
        running = payload.running();
        startEpochMs = payload.startEpochMs() + skew;
        stoppedRtaMs = payload.stoppedRtaMs();
        int previousMarks = newTake ? 0 : markCount;
        markCount = payload.markCount();
        if (markCount > previousMarks) {
            flashedMark = markCount;
            markFlashUntilMs = System.currentTimeMillis() + MARK_FLASH_MS;
        }
    }

    /** S2C {@code freeze_state}. Runs on the client thread. */
    public static void onFreezeState(FreezeStatePayload payload) {
        mobsFrozen = payload.mobs();
        playersFrozen = payload.players();
    }

    /** S2C {@code hide_state}. Runs on the client thread. */
    public static void onHideState(HideStatePayload payload) {
        hideHud = payload.hud();
        hideChat = payload.chat();
        hideNametags = payload.nametags();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.options != null) {
            minecraft.options.hideGui = hideHud;
        }
    }

    // ----- join sync -------------------------------------------------------------------------

    /**
     * Asks the server for the current take and freeze state, once per world the client enters.
     *
     * <p>Called from the HUD layer, which is the only per-frame hook the core library gives a
     * feature; the level-identity check keeps it to exactly one packet per join.
     */
    public static void requestSyncIfNeeded() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            forget(minecraft);
            return;
        }
        if (minecraft.level == syncedLevel) {
            return;
        }
        // A different world: a timer, a FROZEN tag or a hidden HUD from the last server must not
        // bleed into this one. The server answers the request below with the real state.
        forget(minecraft);
        syncedLevel = minecraft.level;
        try {
            Payloads.sendToServer(RequestSyncPayload.INSTANCE);
        } catch (Throwable t) {
            // Vanilla or toolkit-less server: the HUD simply stays at its defaults.
            LOG.debug("[toolkit] could not request a state sync from this server", t);
        }
    }

    /**
     * Fires the mark key. Safe to call when the server does not know the payload - the press is
     * simply dropped rather than disconnecting the client.
     */
    public static void sendMark() {
        try {
            Payloads.sendToServer(MarkPressedPayload.INSTANCE);
        } catch (Throwable t) {
            LOG.debug("[toolkit] mark key press could not be sent to this server", t);
        }
    }

    /**
     * Forgets everything and hands the vanilla HUD switch back to the player, so leaving a world
     * never strands them with a hidden HUD or somebody else's take timer.
     */
    private static void forget(Minecraft minecraft) {
        boolean weHidTheHud = hideHud;
        reset();
        if (weHidTheHud && minecraft != null && minecraft.options != null) {
            minecraft.options.hideGui = false;
        }
    }

    /** Forgets everything. Used when leaving a world would otherwise leave a stale timer on screen. */
    public static void reset() {
        takeNumber = 1;
        running = false;
        startEpochMs = 0L;
        stoppedRtaMs = 0L;
        markCount = 0;
        markFlashUntilMs = 0L;
        flashedMark = 0;
        mobsFrozen = false;
        playersFrozen = false;
        hideHud = false;
        hideChat = false;
        hideNametags = false;
        syncedLevel = null;
    }

    private ClientToolkitState() {
    }
}
