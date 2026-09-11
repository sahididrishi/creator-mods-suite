package dev.riftal.creator.features.toolkit;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric-only glue for the {@code toolkit} feature's player lifecycle.
 *
 * <p>Called by reflection from {@code ToolkitRuntime#bootstrapLoaderGlue()} during
 * {@code Feature#initCommon()}. The reflective hop exists because Fabric discovers entry points
 * only through {@code fabric.mod.json}, and no feature may edit that shared file.
 *
 * <p>Three moments, all of which the toolkit was previously blind to:
 * <ul>
 *   <li><b>join</b> - push the take, freeze and hide state so the HUD is right on the first frame,
 *       and re-arm the sticky-cheat task.</li>
 *   <li><b>respawn</b> - vanilla rebuilt {@code Abilities} from the game mode, so god and fly have
 *       to be re-asserted on the same tick; the freeze lock has to be dropped, because the new
 *       {@code ServerPlayer} carries the old UUID and may well be in another dimension.</li>
 *   <li><b>leave</b> - drop every per-player entry, so nothing is re-applied after a relog and the
 *       maps do not grow for the life of the session.</li>
 * </ul>
 */
public final class ToolkitFabricGlue {

    private static boolean initialised;

    /** Registers the three player-lifecycle listeners. Idempotent. */
    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;

        ServerPlayerEvents.JOIN.register(ToolkitRuntime::onPlayerJoin);
        ServerPlayerEvents.LEAVE.register(ToolkitRuntime::onPlayerLeave);
        ServerPlayerEvents.AFTER_RESPAWN.register(
                (ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) ->
                        ToolkitRuntime.onPlayerRespawn(newPlayer));
    }

    private ToolkitFabricGlue() {
    }
}
