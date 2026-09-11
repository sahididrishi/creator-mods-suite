package dev.riftal.creator.features.colossus.client;

import dev.riftal.creator.features.colossus.entity.AshenColossusEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renderer for the Colossus. Client only - registered from {@code ColossusFeature#initClient()}.
 *
 * <p>The auto-glowing layer draws the {@code _glowmask} texture full-bright, which is what makes
 * the cracks read in a dark arena.
 */
public class ColossusRenderer extends GeoEntityRenderer<AshenColossusEntity> {

    public ColossusRenderer(EntityRendererProvider.Context context) {
        super(context, new ColossusModel());
        this.shadowRadius = 2.0F;
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
