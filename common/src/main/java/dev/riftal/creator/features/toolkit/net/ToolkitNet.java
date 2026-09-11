package dev.riftal.creator.features.toolkit.net;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.core.net.Payloads;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Every server to client send this feature makes goes through here.
 *
 * <p>The toolkit's HUD is a convenience, never a requirement: a crew client without the mod, or a
 * loader that has not finished registering our S2C types, must not turn a {@code /toolkit} command
 * into a stack trace in the middle of a take. So the first failure is logged once and all later
 * sends become no-ops - the commands themselves keep working, only the HUD mirror stops updating.
 */
public final class ToolkitNet {

    private static boolean disabled;

    /** Sends to one player. Never throws. */
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (disabled || player == null) {
            return;
        }
        try {
            Payloads.sendToPlayer(player, payload);
        } catch (Throwable t) {
            disable(t);
        }
    }

    /** Sends to everyone online. Never throws. */
    public static void sendAll(MinecraftServer server, CustomPacketPayload payload) {
        if (disabled || server == null) {
            return;
        }
        try {
            Payloads.sendToAll(server, payload);
        } catch (Throwable t) {
            disable(t);
        }
    }

    /** True once a send has failed and the mirror has been switched off for this session. */
    public static boolean isDisabled() {
        return disabled;
    }

    /** Re-arms sending. Used by the unit tests and by a fresh server binding. */
    public static void reset() {
        disabled = false;
    }

    private static void disable(Throwable t) {
        disabled = true;
        LOG.warn("[toolkit] client HUD sync is unavailable on this connection; commands still work", t);
    }

    private ToolkitNet() {
    }
}
