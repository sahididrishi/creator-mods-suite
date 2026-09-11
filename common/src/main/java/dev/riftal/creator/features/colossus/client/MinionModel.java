package dev.riftal.creator.features.colossus.client;

import dev.riftal.creator.features.colossus.ColossusFeature;
import dev.riftal.creator.features.colossus.entity.AshenMinionEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/** GeckoLib model for the Ashen Minion. Client only. */
public class MinionModel extends DefaultedEntityGeoModel<AshenMinionEntity> {

    public MinionModel() {
        super(ResourceLocation.fromNamespaceAndPath(ColossusFeature.NAMESPACE, "ashen_minion"), true);
    }
}
