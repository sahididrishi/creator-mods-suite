package dev.riftal.creator.features.evolve.client.render;

import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.entity.ApexBeast;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * GeckoLib model for the Apex beast. Client only.
 *
 * <p>{@code DefaultedEntityGeoModel} resolves all three asset paths from one name:
 * {@code geo/entity/apex_beast.geo.json}, {@code animations/entity/apex_beast.animation.json} and
 * {@code textures/entity/apex_beast.png}. Passing {@code true} makes the {@code head} bone follow
 * whatever the renderer is looking at, which is what gives a stage-5 player's beast the player's
 * own head turn.
 *
 * <p>GeckoLib owns the resource-reload story for all three files, so nothing here caches a bake and
 * nothing has to be invalidated by hand on F3+T.
 */
public class ApexBeastModel extends DefaultedEntityGeoModel<ApexBeast> {

    public ApexBeastModel() {
        super(EvolveFeature.id("apex_beast"), true);
    }
}
