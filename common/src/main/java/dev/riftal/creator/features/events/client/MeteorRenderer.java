package dev.riftal.creator.features.events.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.riftal.creator.features.events.entity.MeteorEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

/**
 * Draws the meteor as an oversized, tumbling magma block. No model and no texture file: the block
 * atlas already has everything, which is exactly what the plan asked the MVP renderer to do.
 *
 * <p><b>Client only.</b>
 */
public class MeteorRenderer extends EntityRenderer<MeteorEntity> {

    private static final float SCALE = 1.5F;

    private final BlockRenderDispatcher blocks;

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(MeteorEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        float spin = (entity.tickCount + partialTick) * 12.0F;
        poseStack.translate(0.0D, 0.4D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        poseStack.mulPose(Axis.XP.rotationDegrees(spin * 0.7F));
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        this.blocks.renderSingleBlock(Blocks.MAGMA_BLOCK.defaultBlockState(), poseStack,
                bufferSource, 0x00F000F0, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(MeteorEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
