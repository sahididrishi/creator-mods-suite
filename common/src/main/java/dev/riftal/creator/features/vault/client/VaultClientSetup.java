package dev.riftal.creator.features.vault.client;

import dev.riftal.creator.core.client.ClientRenderers;
import dev.riftal.creator.core.hud.HudLayers;
import dev.riftal.creator.features.vault.VaultFeature;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

/**
 * The single client-only entry point for the {@code vault} feature.
 *
 * <p>{@code VaultFeature#initClient()} calls {@link #init()} and does nothing else, so a dedicated
 * server never loads this class - nor {@link ModelLayerLocation}, the renderer, the model or the
 * HUD layer, all of which are {@code @OnlyIn(Dist.CLIENT)} or reach one.
 *
 * <p><b>Client only.</b>
 */
public final class VaultClientSetup {

    /** The Keeper's single baked model layer. Client-only type, hence not on {@code VaultFeature}. */
    public static final ModelLayerLocation KEEPER_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(VaultFeature.NAMESPACE, "vault_keeper"), "main");

    private static final ResourceLocation HUD_ID =
            ResourceLocation.fromNamespaceAndPath(VaultFeature.NAMESPACE, "altar_status");

    private static boolean initialised;

    /** Idempotent: NeoForge drives {@code initClient} from more than one registration event. */
    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;

        ClientRenderers.modelLayer(KEEPER_LAYER, VaultKeeperModel::createBodyLayer);
        ClientRenderers.entityRenderer(VaultFeature.VAULT_KEEPER, VaultKeeperRenderer::new);
        HudLayers.register(HUD_ID, VaultHud::render);
    }

    private VaultClientSetup() {
    }
}
