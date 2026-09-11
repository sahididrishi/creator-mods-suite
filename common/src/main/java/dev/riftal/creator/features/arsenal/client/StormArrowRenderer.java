package dev.riftal.creator.features.arsenal.client;

import dev.riftal.creator.features.arsenal.ArsenalFeature;
import dev.riftal.creator.features.arsenal.entity.StormArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Storm arrow renderer. {@code ArrowRenderer} is abstract on 1.21.1 and already draws the arrow
 * geometry, so the only thing left to supply is the texture. <b>Client only.</b>
 */
public class StormArrowRenderer extends ArrowRenderer<StormArrowEntity> {

    private static final ResourceLocation TEXTURE =
            ArsenalFeature.res("textures/entity/projectiles/storm_arrow.png");

    public StormArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(StormArrowEntity entity) {
        return TEXTURE;
    }
}
