package dev.riftal.creator.features.powers.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Fabric-only glue for the {@code powers} feature's key mappings.
 *
 * <p>Called by reflection from {@code PowersClient} during {@code Feature#initClient()} - which on
 * Fabric runs inside the {@code ClientModInitializer} entrypoint, i.e. before
 * {@code Minecraft#options} exists. That ordering is not optional:
 * {@code KeyBindingRegistryImpl#registerKeyBinding} throws
 * {@code IllegalStateException("GameOptions has already been initialised")} if it is called any
 * later.
 *
 * <p>The reflective hop exists because Fabric discovers entrypoints only through
 * {@code fabric.mod.json}, and no feature may edit that shared file.
 */
public final class PowersFabricClientGlue {

    private static boolean initialised;

    /** Registers the six mappings with the controls screen and starts polling them each client tick. */
    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;

        for (KeyMapping mapping : PowerKeys.list()) {
            KeyBindingHelper.registerKeyBinding(mapping);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.screen == null) {
                PowerKeys.poll();
            }
        });
        PowersClient.markKeyPollingWired();
    }

    private PowersFabricClientGlue() {
    }
}
