package dev.riftal.creator.features.toolkit.net;

import dev.riftal.creator.features.toolkit.ToolkitRuntime;
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

    /** A client just entered a world and wants the current take and freeze state. */
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
    }

    private static boolean ready(Map<UUID, Integer> seen, ServerPlayer sender, int now, int cooldown) {
        Integer last = seen.get(sender.getUUID());
        if (last != null && now - last < cooldown && now >= last) {
            return false;
        }
        if (seen.size() > 256) {
            seen.clear();
        }
        seen.put(sender.getUUID(), now);
        return true;
    }

    private ToolkitPayloadHandlers() {
    }
}
