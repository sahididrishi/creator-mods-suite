package dev.riftal.creator.features.toolkit;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.core.command.SilentMode;
import dev.riftal.creator.features.toolkit.cheat.CheatManager;
import dev.riftal.creator.features.toolkit.command.HideCommands;
import dev.riftal.creator.features.toolkit.freeze.FreezeManager;
import dev.riftal.creator.features.toolkit.net.ToolkitNet;
import dev.riftal.creator.features.toolkit.net.ToolkitPayloadHandlers;
import dev.riftal.creator.features.toolkit.take.TakeManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Owns the boundary between one server session and the next, and the three per-player moments the
 * toolkit has to react to: join, respawn and disconnect.
 *
 * <p>Freeze flags and the live take are in-memory statics - fast to read from the freeze mixin, but
 * they must not survive into a different world. There is no per-feature server-start hook in the
 * core library, so instead every entry point ({@code /toolkit ...}, every C2S payload) calls
 * {@link #bind(MinecraftServer)} first: the first call for a new server wipes the previous one's
 * state. {@code ToolkitMinecraftServerMixin} closes the other end by flushing a running take when
 * the server shuts down.
 *
 * <p>{@link #onPlayerJoin}, {@link #onPlayerRespawn} and {@link #onPlayerLeave} are called by the
 * loader glue in {@code fabric/} and {@code neoforge/} - see {@link #bootstrapLoaderGlue()}.
 */
public final class ToolkitRuntime {

    private static final String FABRIC_GLUE =
            "dev.riftal.creator.features.toolkit.ToolkitFabricGlue";

    private static MinecraftServer owner;
    private static boolean glueBootstrapped;

    /**
     * Starts the loader's player-lifecycle hooks.
     *
     * <p>NeoForge finds its own glue through an auto-scanned {@code @EventBusSubscriber} class, so
     * there is nothing to do there. Fabric discovers entry points only through
     * {@code fabric.mod.json}, which no feature may edit, so the Fabric half is one
     * {@code Class.forName} into this feature's own class in the {@code fabric} module - the same
     * hop the {@code powers} feature uses for its key mappings.
     */
    public static void bootstrapLoaderGlue() {
        if (glueBootstrapped) {
            return;
        }
        glueBootstrapped = true;
        try {
            Class.forName(FABRIC_GLUE).getMethod("init").invoke(null);
        } catch (ClassNotFoundException notFabric) {
            // NeoForge: ToolkitNeoForgeEvents is an auto-scanned @EventBusSubscriber class instead.
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOG.warn("[toolkit] Fabric player-lifecycle glue did not start; god/fly will not be "
                    + "re-applied on respawn and the HUD will sync a tick later on join", failure);
        }
    }

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

    /**
     * A player finished loading into the world.
     *
     * <p>Three jobs, none of which can wait for the client to ask: push the authoritative take,
     * freeze and hide state so the HUD is right on the first frame; re-assert god/fly, which vanilla
     * rebuilds from the game mode on a fresh {@code ServerPlayer}; and arm the re-apply task, which
     * otherwise only ever starts from the command itself and so is missing for the whole of any
     * session the director did not re-run {@code /toolkit cheat} in.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        bind(player.getServer());
        CheatManager.onPlayerJoin(player);
        ToolkitNet.send(player, TakeManager.payload());
        ToolkitNet.send(player, FreezeManager.payload());
        ToolkitNet.send(player, HideCommands.stateFor(player));
    }

    /** A fresh {@code ServerPlayer} replaced a dead one: abilities and the freeze lock are stale. */
    public static void onPlayerRespawn(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        bind(player.getServer());
        CheatManager.onPlayerRespawn(player);
        FreezeManager.forget(player.getUUID());
        ToolkitNet.send(player, TakeManager.payload());
        ToolkitNet.send(player, FreezeManager.payload());
        ToolkitNet.send(player, HideCommands.stateFor(player));
    }

    /** A player left: drop every per-player entry so nothing is re-applied after a relog. */
    public static void onPlayerLeave(ServerPlayer player) {
        if (player == null) {
            return;
        }
        FreezeManager.forget(player.getUUID());
        HideCommands.forget(player.getUUID());
        ToolkitPayloadHandlers.forget(player.getUUID());
    }

    /** Called from the shutdown mixin: writes the take footer, then drops everything. */
    public static void onServerStopping(MinecraftServer server) {
        if (owner != null && server != owner) {
            return;
        }
        if (server != null) {
            TakeManager.abort(server, "server_stop");
            // Silent mode writes sendCommandFeedback and logAdminCommands into the world's real
            // game rules, and those live in level.dat. The loader's SilentMode.reset() only zeroes
            // core's statics - it never touches the world - so without this a world closed mid-
            // shoot is saved permanently silent. We are at HEAD of stopServer, with saveAllChunks
            // still ahead of us, so the restored values are the ones that get written.
            //
            // Two steps on purpose. SilentMode.set is the tidy path and also clears core's flag,
            // but it is a no-op once the loader's own stopServer listener has run, and mixin order
            // between two configs at one injection point is undefined. HideCommands keeps its own
            // snapshot, which does not depend on that race.
            boolean restored = false;
            if (SilentMode.isSilent()) {
                SilentMode.set(server, false);
                restored = true;
            }
            restored |= HideCommands.restoreGameRules(server);
            if (restored) {
                LOG.info("[toolkit] silent mode was on at shutdown; game rules restored");
            }
        }
        clear();
        owner = null;
    }

    private static void clear() {
        FreezeManager.reset();
        TakeManager.reset();
        CheatManager.reset();
        HideCommands.reset();
        ToolkitPayloadHandlers.reset();
        ToolkitNet.reset();
    }

    private ToolkitRuntime() {
    }
}
