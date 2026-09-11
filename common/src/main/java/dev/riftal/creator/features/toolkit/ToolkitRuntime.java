package dev.riftal.creator.features.toolkit;

import dev.riftal.creator.features.toolkit.cheat.CheatManager;
import dev.riftal.creator.features.toolkit.command.HideCommands;
import dev.riftal.creator.features.toolkit.freeze.FreezeManager;
import dev.riftal.creator.features.toolkit.net.ToolkitNet;
import dev.riftal.creator.features.toolkit.take.TakeManager;
import net.minecraft.server.MinecraftServer;

/**
 * Owns the boundary between one server session and the next.
 *
 * <p>Freeze flags and the live take are in-memory statics - fast to read from the freeze mixin, but
 * they must not survive into a different world. There is no per-feature server-start hook in the
 * core library, so instead every entry point ({@code /toolkit ...}, every C2S payload) calls
 * {@link #bind(MinecraftServer)} first: the first call for a new server wipes the previous one's
 * state. {@code ToolkitMinecraftServerMixin} closes the other end by flushing a running take when
 * the server shuts down.
 */
public final class ToolkitRuntime {

    private static MinecraftServer owner;

    /** Marks {@code server} as the live session, clearing stale state from a previous one. */
    public static MinecraftServer bind(MinecraftServer server) {
        if (server != null && server != owner) {
            clear();
            owner = server;
        }
        return server;
    }

    /** The server this feature's in-memory state belongs to, or null before the first command. */
    public static MinecraftServer server() {
        return owner;
    }

    /** Called from the shutdown mixin: writes the take footer, then drops everything. */
    public static void onServerStopping(MinecraftServer server) {
        if (owner != null && server != owner) {
            return;
        }
        if (server != null) {
            TakeManager.abort(server, "server_stop");
        }
        clear();
        owner = null;
    }

    private static void clear() {
        FreezeManager.reset();
        TakeManager.reset();
        CheatManager.reset();
        HideCommands.reset();
        ToolkitNet.reset();
    }

    private ToolkitRuntime() {
    }
}
