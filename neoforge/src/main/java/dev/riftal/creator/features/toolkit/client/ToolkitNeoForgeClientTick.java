package dev.riftal.creator.features.toolkit.client;

import dev.riftal.creator.Constants;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Per-tick and disconnect hooks on NeoForge. Both are <em>game</em> bus events, so they need their
 * own {@code @EventBusSubscriber} - the bus is chosen per class, not per method.
 *
 * <p>The join sync used to ride on the HUD layer's {@code render}. On NeoForge that layer is
 * registered into {@code Gui}'s {@code LayeredDraw}, which vanilla gates on
 * {@code !options.hideGui}, so with the HUD hidden the layer never ran and the client never asked
 * the server for anything. Polling happens once per client tick with {@code consumeClick()}, never
 * per frame and never with {@code isDown()}: a held key must fire exactly once.
 */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ToolkitNeoForgeClientTick {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ToolkitFeature.enabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ToolkitClient.clientTick(minecraft.player != null && minecraft.screen == null);
        ToolkitClient.markClientTickWired();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (!ToolkitFeature.enabled()) {
            return;
        }
        ClientToolkitState.onDisconnect();
    }

    private ToolkitNeoForgeClientTick() {
    }
}
