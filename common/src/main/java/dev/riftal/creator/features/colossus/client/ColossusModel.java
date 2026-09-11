package dev.riftal.creator.features.colossus.client;

import dev.riftal.creator.features.colossus.BossPhase;
import dev.riftal.creator.features.colossus.ColossusFeature;
import dev.riftal.creator.features.colossus.entity.AshenColossusEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * GeckoLib model for the Colossus. Client only.
 *
 * <p>{@code DefaultedEntityGeoModel} resolves the three asset paths from one name:
 * {@code geo/entity/ashen_colossus.geo.json}, {@code animations/entity/ashen_colossus.animation.json}
 * and {@code textures/entity/ashen_colossus.png}. Passing {@code true} makes the {@code head} bone
 * follow the player.
 *
 * <p>In phase 3 the texture swaps to the cracked-and-glowing variant;
 * {@code AutoGlowingGeoLayer} then picks up {@code ashen_colossus_enraged_glowmask.png} on its own.
 */
public class ColossusModel extends DefaultedEntityGeoModel<AshenColossusEntity> {

    private static final ResourceLocation ENRAGED_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ColossusFeature.NAMESPACE, "textures/entity/ashen_colossus_enraged.png");

    public ColossusModel() {
        super(ResourceLocation.fromNamespaceAndPath(ColossusFeature.NAMESPACE, "ashen_colossus"), true);
    }

    @Override
    public ResourceLocation getTextureResource(AshenColossusEntity animatable) {
        return animatable.getPhase() >= BossPhase.P3.index()
                ? ENRAGED_TEXTURE
                : super.getTextureResource(animatable);
    }
}
