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
 * into a stack trace in the middle of a take. So a throw is caught and logged and the caller
 * carries on.
 *
 * <p>It is deliberately <em>not</em> a latch. The loader helpers already skip an unreachable player
 * one at a time ({@code NeoForgeNetworkHelper#canReceive}, {@code FabricNetworkHelper}'s
 * null-connection check), so a throw here means something genuinely unexpected happened to
 * <em>one</em> connection - and switching every later broadcast off because of it froze the take
 * timer, the mark flash and the {@code FROZEN} tag on every client, including the director's, for
 * the rest of the shoot, with nothing but a line in the log to say why. The log line is rate
 * limited instead, so a permanently broken connection cannot flood the file either.
 */
public final class ToolkitNet {

    /** Don't log the same failure more than once per this many sends. */
    private static final int LOG_EVERY = 200;

    private static int failures;

    /** Sends to one player. Never throws. */
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player == null) {
            return;
        }
        try {
            Payloads.sendToPlayer(player, payload);
        } catch (Throwable t) {
            noteFailure(t);
        }
    }

    /** Sends to everyone online. Never throws. */
    public static void sendAll(MinecraftServer server, CustomPacketPayload payload) {
        if (server == null) {
            return;
        }
        try {
            Payloads.sendToAll(server, payload);
        } catch (Throwable t) {
            noteFailure(t);
        }
    }

    /** How many sends have failed in this session. Zero on a healthy server. */
    public static int failureCount() {
        return failures;
    }

    /** Clears the failure counter. Used by the unit tests and by a fresh server binding. */
    public static void reset() {
        failures = 0;
    }

    private static void noteFailure(Throwable t) {
        if (failures++ % LOG_EVERY == 0) {
            LOG.warn("[toolkit] a client HUD sync packet could not be delivered; commands still "
                    + "work and other players are unaffected", t);
        }
    }

    private ToolkitNet() {
    }
}
