package dev.riftal.creator.features.powers.client;

import dev.riftal.creator.core.hud.HudLayers;
import net.minecraft.resources.ResourceLocation;

import static dev.riftal.creator.Constants.LOG;

/**
 * Client entry point for the Power Kit: builds the six key mappings, puts the cooldown row on the
 * HUD, and hands the mappings to whichever loader is running so they also show up in
 * Options -&gt; Controls and survive a rebind.
 *
 * <p><b>Client only.</b> Reached from {@code PowersFeature#initClient()} and from nowhere else.
 *
 * <h2>Why the loader hop is reflective on Fabric</h2>
 * Key mappings are the one thing in this feature that genuinely cannot be registered from common
 * code. NeoForge wants {@code RegisterKeyMappingsEvent} on the mod bus (reached without any shared
 * file by an auto-scanned {@code @EventBusSubscriber} class in the {@code neoforge} module) and
 * Fabric wants {@code KeyBindingHelper} during the client entrypoint. Fabric discovers entrypoints
 * only through {@code fabric.mod.json}, which no feature may edit, so the Fabric half is a single
 * {@code Class.forName} into this feature's own class in the {@code fabric} module. If it is ever
 * missing the keys still <em>work</em> - constructing a {@code KeyMapping} is enough for
 * {@code consumeClick()} - they just would not be listed in the controls screen, and
 * {@link #pollKeysIfNotWired()} keeps them polling.
 */
public final class PowersClient {

    private static final String FABRIC_GLUE =
            "dev.riftal.creator.features.powers.client.PowersFabricClientGlue";

    private static boolean initialised;
    private static boolean keyPollingWired;

    /** Builds the keys and registers the HUD row. Idempotent. */
    public static void init(ResourceLocation hudLayerId) {
        if (initialised) {
            return;
        }
        initialised = true;

        PowerKeys.all();
        HudLayers.register(hudLayerId, new PowerHudLayer());
        bootstrapFabricGlue();
    }

    /** Called by the loader glue once it owns the per-tick key polling. */
    public static void markKeyPollingWired() {
        keyPollingWired = true;
    }

    /** Fallback polling for the (unexpected) case that no loader glue claimed the job. */
    public static void pollKeysIfNotWired() {
        if (!keyPollingWired) {
            PowerKeys.poll();
        }
    }

    private static void bootstrapFabricGlue() {
        try {
            Class.forName(FABRIC_GLUE).getMethod("init").invoke(null);
        } catch (ClassNotFoundException notFabric) {
            // NeoForge: the glue is an auto-scanned @EventBusSubscriber class instead.
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOG.warn("[powers] Fabric key-mapping glue did not start; the ability keys still fire but "
                    + "will not be listed in Options > Controls", failure);
        }
    }

    private PowersClient() {
    }
}
