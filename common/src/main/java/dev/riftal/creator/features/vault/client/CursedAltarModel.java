package dev.riftal.creator.features.vault.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The Cursed Altar's floating crystal, as a baked model layer.
 *
 * <p>Only the crystal. The pedestal stays a plain blockstate model
 * ({@code models/block/cursed_altar_pedestal.json}), because it never moves and a static block
 * model is cheaper, lights correctly and survives a resource reload with no code. The crystal is
 * the part the plan wants alive - "the chains snap taut, the crystal rises and spins" - so it is
 * split out and handed to {@link CursedAltarRenderer}.
 *
 * <p>Geometry matches the box the old, motionless block model drew: 6 wide, 4 tall, 6 deep, sitting
 * where {@code [5,12,5]-[11,16,11]} used to be. The pivot is the centre of that box so a spin is a
 * spin and not an orbit.
 *
 * <p>The sheet is 32x32, not 16x16: the UV net of a 6x4x6 box is {@code 2 * (6 + 6) = 24} pixels
 * across, so a 16-wide texture cannot hold it. See {@code tools/make_placeholder_textures.py}.
 *
 * <p><b>Client only.</b>
 */
public final class CursedAltarModel {

    /** Child part name inside the baked layer. */
    public static final String CRYSTAL = "crystal";

    /** Texture sheet size; see the class javadoc for why it is not 16. */
    public static final int TEXTURE_SIZE = 32;

    /** Registered through {@code ClientRenderers.modelLayer} in {@code VaultClientSetup#init()}. */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
                CRYSTAL,
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 4.0F, 6.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private CursedAltarModel() {
    }
}
