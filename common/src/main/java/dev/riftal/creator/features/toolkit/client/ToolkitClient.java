package dev.riftal.creator.features.toolkit.client;

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
 */
public final class ToolkitClient {

    /** HUD layer id, {@code creator_toolkit:take_timer}. */
    public static final ResourceLocation TAKE_HUD =
            ResourceLocation.fromNamespaceAndPath(ToolkitFeature.NAMESPACE, "take_timer");

    private static boolean initialised;

    /** Registers the take HUD. Idempotent - NeoForge calls {@code initClient()} more than once. */
    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        HudLayers.register(TAKE_HUD, new TakeHudLayer());
    }

    private ToolkitClient() {
    }
}
