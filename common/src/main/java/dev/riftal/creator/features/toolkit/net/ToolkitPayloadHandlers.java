package dev.riftal.creator.features.toolkit.net;

import dev.riftal.creator.features.toolkit.ToolkitRuntime;
import dev.riftal.creator.features.toolkit.command.HideCommands;
import dev.riftal.creator.features.toolkit.freeze.FreezeManager;
import dev.riftal.creator.features.toolkit.take.TakeManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side handling of the two C2S payloads.
 *
 * <p>Both are attacker controlled, so both are re-checked here: the mark key needs the same
 * permission level the command does, and the sync request is rate limited so a scripted client
 * cannot use it to make the server chatter.
 *
 * <p>The cooldown tables are keyed by player and pruned on disconnect
 * ({@code ToolkitRuntime#onPlayerLeave}) and on a server change, so they neither grow for the life
 * of the JVM nor - as an eviction-by-size policy would - reset everybody's cooldown at once the
 * moment a busy server passes its 257th unique visitor.
 */
public final class ToolkitPayloadHandlers {

    /** Minimum ticks between two accepted sync requests from one player. */
    private static final int SYNC_COOLDOWN_TICKS = 20;

    /** Minimum ticks between two accepted mark presses from one player. */
    private static final int MARK_COOLDOWN_TICKS = 5;

    private static final Map<UUID, Integer> LAST_SYNC = new HashMap<>();
    private static final Map<UUID, Integer> LAST_MARK = new HashMap<>();

    /** The mark key was pressed on a client. */
    public static void onMarkPressed(MarkPressedPayload payload, ServerPlayer sender) {
        if (sender == null) {
            return;
        }
        MinecraftServer server = sender.getServer();
        if (server == null || !sender.hasPermissions(2)) {
            return;
        }
        if (!ready(LAST_MARK, sender, server.getTickCount(), MARK_COOLDOWN_TICKS)) {
            return;
        }
        ToolkitRuntime.bind(server);
        TakeManager.mark(server, sender.getGameProfile().getName(), "");
    }

    /** A client just entered a world and wants the current take, freeze and hide state. */
    public static void onRequestSync(RequestSyncPayload payload, ServerPlayer sender) {
        if (sender == null) {
            return;
        }
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return;
        }
        if (!ready(LAST_SYNC, sender, server.getTickCount(), SYNC_COOLDOWN_TICKS)) {
            return;
        }
        ToolkitRuntime.bind(server);
        ToolkitNet.send(sender, TakeManager.payload());
        ToolkitNet.send(sender, FreezeManager.payload());
        // The hide flags belong here too. The client throws its own copy away on every world
        // change, so a rejoining player who does not get them back leaves the server's mirror
        // stale - and the next /toolkit hide chat on would merge against it and re-hide a HUD
        // nobody asked to hide.
        ToolkitNet.send(sender, HideCommands.stateFor(sender));
    }

    /** Forgets one player's cooldowns, on disconnect. */
    public static void forget(UUID player) {
        if (player != null) {
            LAST_SYNC.remove(player);
            LAST_MARK.remove(player);
        }
    }

    /** Drops every cooldown. Called when the server this state belongs to goes away. */
    public static void reset() {
        LAST_SYNC.clear();
        LAST_MARK.clear();
    }

    private static boolean ready(Map<UUID, Integer> seen, ServerPlayer sender, int now, int cooldown) {
        Integer last = seen.get(sender.getUUID());
        if (last != null && now - last < cooldown && now >= last) {
            return false;
        }
        seen.put(sender.getUUID(), now);
        return true;
    }

    private ToolkitPayloadHandlers() {
    }
}
