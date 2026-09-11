package dev.riftal.creator.features.colossus.client;

import dev.riftal.creator.features.colossus.entity.AshBombEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/**
 * The ash bomb is drawn as its item (a lump of magma cream) rather than a custom model - it is
 * on screen for under a second and is mostly fire particles anyway. Client only.
 */
public class AshBombRenderer extends ThrownItemRenderer<AshBombEntity> {

    public AshBombRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}
