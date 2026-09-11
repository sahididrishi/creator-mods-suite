package dev.riftal.creator.features.toolkit.client;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.core.hud.HudLayers;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import net.minecraft.resources.ResourceLocation;

/**
 * Everything the toolkit does on the client, in one place. <strong>Client only</strong> - called
 * from {@code ToolkitFeature#initClient()} and nowhere else, so a dedicated server never loads it.
 *
 * <p>The S2C receivers are <em>not</em> here: they are registered in
 * {@code ToolkitFeature#registerContent()} on both sides, because the loader helpers register the
 * payload type in the same call as the receiver and a dedicated server has to know the types to be
 * allowed to send them.
 *
 * <h2>Why the loader hop is reflective on Fabric</h2>
 * Key mappings cannot be registered from common code. NeoForge wants
 * {@code RegisterKeyMappingsEvent} on the mod bus (reached without touching any shared file through
 * an auto-scanned {@code @EventBusSubscriber} class in the {@code neoforge} module); Fabric wants
 * {@code KeyBindingHelper} during the client entry point, and Fabric discovers entry points only
 * through {@code fabric.mod.json}, which no feature may edit. So the Fabric half is one
 * {@code Class.forName} into this feature's own class in the {@code fabric} module. If it were ever
 * missing the mark key would still <em>fire</em> - constructing a {@code KeyMapping} is enough for
 * {@code consumeClick()} - it just would not be listed in the controls screen, and
 * {@link #tickIfNotWired()} keeps it polling.
 */
public final class ToolkitClient {

    /** HUD layer id, {@code creator_toolkit:take_timer}. */
    public static final ResourceLocation TAKE_HUD =
            ResourceLocation.fromNamespaceAndPath(ToolkitFeature.NAMESPACE, "take_timer");

    private static final String FABRIC_GLUE =
            "dev.riftal.creator.features.toolkit.client.ToolkitFabricClientGlue";

    private static boolean initialised;
    private static boolean clientTickWired;

    /** Builds the mark key and registers the take HUD. Idempotent - NeoForge calls this more than once. */
    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        ToolkitKeys.markKey();
        HudLayers.register(TAKE_HUD, new TakeHudLayer());
        bootstrapFabricGlue();
    }

    /** Called by the loader glue once it owns the per-tick job. */
    public static void markClientTickWired() {
        clientTickWired = true;
    }

    /**
     * One client tick: ask the server for the current state if we have just entered a world, then
     * drain the mark key.
     *
     * <p>Called from the loader's own client-tick event, never from a render callback. The join
     * sync used to ride on {@code TakeHudLayer#render}, and on NeoForge that layer sits inside
     * {@code Gui}'s {@code LayeredDraw}, which vanilla gates on {@code !options.hideGui} - so with
     * the HUD hidden the layer never ran and the sync never fired. {@code consumeClick()} must not
     * be drained by the renderer either: a 200 fps client would swallow presses between ticks.
     *
     * @param acceptKeys false while a screen is open or before the player exists, so the mark key
     *                   does not fire behind a menu - the sync housekeeping still runs
     */
    public static void clientTick(boolean acceptKeys) {
        ClientToolkitState.requestSyncIfNeeded();
        if (acceptKeys) {
            ToolkitKeys.poll();
        }
    }

    /** Fallback for the (unexpected) case that no loader glue claimed the per-tick job. */
    public static void tickIfNotWired() {
        if (!clientTickWired) {
            clientTick(true);
        }
    }

    private static void bootstrapFabricGlue() {
        try {
            Class.forName(FABRIC_GLUE).getMethod("init").invoke(null);
        } catch (ClassNotFoundException notFabric) {
            // NeoForge: the glue is two auto-scanned @EventBusSubscriber classes instead.
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOG.warn("[toolkit] Fabric client glue did not start; the mark key still fires but will "
                    + "not be listed in Options > Controls", failure);
        }
    }

    private ToolkitClient() {
    }
}
