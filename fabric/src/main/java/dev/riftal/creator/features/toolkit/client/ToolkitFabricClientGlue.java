package dev.riftal.creator.features.toolkit.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Fabric-only glue for the {@code toolkit} feature's client hooks.
 *
 * <p>Called by reflection from {@code ToolkitClient#init()} during {@code Feature#initClient()} -
 * which on Fabric runs inside the {@code ClientModInitializer} entry point, i.e. before
 * {@code Minecraft#options} exists. That ordering is not optional:
 * {@code KeyBindingRegistryImpl#registerKeyBinding} throws
 * {@code IllegalStateException("GameOptions has already been initialised")} if it is called any
 * later.
 *
 * <p>The reflective hop exists because Fabric discovers entry points only through
 * {@code fabric.mod.json}, and no feature may edit that shared file.
 */
public final class ToolkitFabricClientGlue {

    private static boolean initialised;

    /** Lists the mark key in Options &gt; Controls and starts the per-tick hooks. */
    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;

        for (KeyMapping mapping : ToolkitKeys.list()) {
            KeyBindingHelper.registerKeyBinding(mapping);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client ->
                ToolkitClient.clientTick(client.player != null && client.screen == null));

        // Hand the vanilla HUD switch back on the way out, not on the next join: without this the
        // disconnected ClientLevel stayed referenced and a hidden HUD outlived the world.
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> ClientToolkitState.onDisconnect());

        ToolkitClient.markClientTickWired();
    }

    private ToolkitFabricClientGlue() {
    }
}
