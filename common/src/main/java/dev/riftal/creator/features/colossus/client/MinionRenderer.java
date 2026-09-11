package dev.riftal.creator.features.colossus.client;

import dev.riftal.creator.features.colossus.entity.AshenMinionEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Renderer for the Ashen Minion. Client only. */
public class MinionRenderer extends GeoEntityRenderer<AshenMinionEntity> {

    public MinionRenderer(EntityRendererProvider.Context context) {
        super(context, new MinionModel());
        this.shadowRadius = 0.4F;
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
