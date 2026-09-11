package dev.riftal.creator.features.vault.client;

import dev.riftal.creator.features.vault.entity.VaultKeeper;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/**
 * The Vault Keeper's model: the vanilla humanoid mesh, inflated, on a 64x64 sheet.
 *
 * <p>Vanilla geometry rather than a GeckoLib rig - see {@code ASSETS.md} for why and for what a
 * real artist should replace it with. The vanilla humanoid already animates walking, attacking and
 * head tracking, which is everything the fight in the clip needs.
 *
 * <p><b>Client only.</b>
 */
public class VaultKeeperModel extends HumanoidModel<VaultKeeper> {

    /** Inflation applied to every humanoid cube, so the Keeper reads as heavier than a zombie. */
    public static final float INFLATE = 0.45F;

    public VaultKeeperModel(ModelPart root) {
        super(root);
    }

    /** Registered through {@code ClientRenderers.modelLayer} in {@code VaultFeature#initClient()}. */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(INFLATE), 0.0F);
        return LayerDefinition.create(mesh, 64, 64);
    }
}
