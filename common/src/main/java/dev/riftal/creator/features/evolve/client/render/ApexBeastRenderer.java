package dev.riftal.creator.features.evolve.client.render;

import dev.riftal.creator.features.evolve.entity.ApexBeast;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the real {@link ApexBeast} entity. Ordinary vanilla {@code MobRenderer}, registered through
 * {@code ClientRenderers.entityRenderer} from {@code EvolveFeature#initClient()}.
 *
 * <p>Client only.
 */
public class ApexBeastRenderer extends MobRenderer<ApexBeast, ApexBeastModel<ApexBeast>> {

    public ApexBeastRenderer(EntityRendererProvider.Context context) {
        super(context, new ApexBeastModel<>(context.bakeLayer(ApexBeastModel.LAYER)), 1.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(ApexBeast entity) {
        return ApexBeastModel.TEXTURE;
    }
}
