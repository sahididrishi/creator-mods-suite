package dev.riftal.creator.features.toolkit;

import dev.riftal.creator.Constants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * NeoForge-only glue for the {@code toolkit} feature's player lifecycle.
 *
 * <p>Auto-discovered by FML's {@code AutomaticEventSubscriber}, so it needs no entry in the shared
 * {@code neoforge.mods.toml}. These are <em>game</em> bus events; the bus is chosen per class, not
 * per method, which is why the client-side hooks live in their own classes.
 *
 * <p>Guarded by the feature toggle: the class is scanned whether or not {@code toolkit} is enabled,
 * and a switched-off feature must change nothing at all.
 *
 * <p>The Fabric equivalent is {@code ToolkitFabricGlue}; both funnel into {@link ToolkitRuntime},
 * where the actual work lives.
 */
@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ToolkitNeoForgeEvents {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ToolkitRuntime.onPlayerJoin(serverPlayer(event.getEntity()));
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        ToolkitRuntime.onPlayerRespawn(serverPlayer(event.getEntity()));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ToolkitRuntime.onPlayerLeave(serverPlayer(event.getEntity()));
    }

    /**
     * These events carry a {@code Player}; only a real {@code ServerPlayer} has the connection and
     * the attachment we care about. {@link ToolkitRuntime} treats null as "nothing to do", and
     * {@link ToolkitFeature#enabled()} keeps a disabled feature inert.
     */
    private static ServerPlayer serverPlayer(Player player) {
        if (!ToolkitFeature.enabled()) {
            return null;
        }
        return player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    private ToolkitNeoForgeEvents() {
    }
}
