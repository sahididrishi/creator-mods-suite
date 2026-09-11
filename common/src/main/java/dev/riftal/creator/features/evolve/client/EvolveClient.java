package dev.riftal.creator.features.evolve.client;

import dev.riftal.creator.core.client.ClientRenderers;
import dev.riftal.creator.core.hud.HudLayers;
import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.client.hud.EvolutionHud;
import dev.riftal.creator.features.evolve.client.render.ApexBeastModel;
import dev.riftal.creator.features.evolve.client.render.ApexBeastRenderer;
import dev.riftal.creator.features.evolve.net.EvolveClientSink;
import dev.riftal.creator.features.evolve.net.EvolvePayloads;
import dev.riftal.creator.features.evolve.net.SyncEvolutionPayload;
import dev.riftal.creator.features.evolve.net.TransformFxPayload;
import dev.riftal.creator.features.evolve.net.XpPopupPayload;

/**
 * Client half of Evolve: the payload receivers, the HUD layer, the beast renderer and its model
 * layer.
 *
 * <p><b>Client only.</b> Reached exclusively from {@code EvolveFeature#initClient()}, which never
 * runs on a dedicated server, so no class in this package is ever loaded there.
 */
public final class EvolveClient implements EvolveClientSink {

    private static final EvolveClient INSTANCE = new EvolveClient();

    private static boolean initialised;

    /** Idempotent: NeoForge drives {@code initClient()} from several registration events. */
    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;

        EvolvePayloads.setSink(INSTANCE);
        ClientRenderers.modelLayer(ApexBeastModel.LAYER, ApexBeastModel::createBodyLayer);
        ClientRenderers.entityRenderer(EvolveFeature.apexBeast(), ApexBeastRenderer::new);
        HudLayers.register(EvolveFeature.id("evolution_bar"), EvolutionHud::render);
    }

    @Override
    public void onSync(SyncEvolutionPayload payload) {
        ClientEvolutionCache.put(payload.playerId(), payload.stage(), payload.xp(),
                payload.transforming(), payload.modelOverride());
    }

    @Override
    public void onTransformFx(TransformFxPayload payload) {
        if (payload.kind() == TransformFxPayload.START) {
            ClientEvolutionCache.startTransform(payload.playerId(), payload.ticks(),
                    payload.targetStage());
        } else {
            ClientEvolutionCache.stopTransform(payload.playerId());
        }
    }

    @Override
    public void onXpPopup(XpPopupPayload payload) {
        ClientEvolutionCache.addPopup(payload.amount(), payload.source());
    }

    private EvolveClient() {
    }
}
