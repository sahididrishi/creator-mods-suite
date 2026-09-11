package dev.riftal.creator.features.vault.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftal.creator.features.vault.VaultFeature;
import dev.riftal.creator.features.vault.entity.VaultKeeper;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderer for the Vault Keeper. Scales the humanoid model up so the mini-boss towers over a
 * player without needing its own rig.
 *
 * <p><b>Client only.</b>
 */
public class VaultKeeperRenderer extends HumanoidMobRenderer<VaultKeeper, VaultKeeperModel> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            VaultFeature.NAMESPACE, "textures/entity/vault_keeper.png");

    private static final float MODEL_SCALE = 1.18F;

    public VaultKeeperRenderer(EntityRendererProvider.Context context) {
        super(context, new VaultKeeperModel(context.bakeLayer(VaultClientSetup.KEEPER_LAYER)), 0.7F);
    }

    @Override
    public ResourceLocation getTextureLocation(VaultKeeper entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(VaultKeeper entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }
}
