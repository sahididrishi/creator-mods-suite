package dev.riftal.creator.features.evolve.client.render;

import dev.riftal.creator.features.evolve.entity.ApexBeast;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renderer for the Apex beast. Client only - registered from {@code EvolveFeature#initClient()}.
 *
 * <p>Draws the real {@code /summon}ed entity <em>and</em>, through
 * {@link PlayerRenderSwap}, the per-player render proxy that stands in for a stage-5 player's body.
 * Both paths therefore get the same geo, the same animations and the same shadow.
 */
public class ApexBeastRenderer extends GeoEntityRenderer<ApexBeast> {

    public ApexBeastRenderer(EntityRendererProvider.Context context) {
        super(context, new ApexBeastModel());
        this.shadowRadius = 1.5F;
    }
}
