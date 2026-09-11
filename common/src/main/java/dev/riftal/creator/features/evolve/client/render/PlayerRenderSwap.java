package dev.riftal.creator.features.evolve.client.render;

import static dev.riftal.creator.Constants.LOG;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.client.ClientEvolutionCache;
import dev.riftal.creator.features.evolve.data.EvolutionData;
import dev.riftal.creator.features.evolve.entity.ApexBeast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Draws the Apex beast where a stage-5 player's body would be.
 *
 * <p>Called from {@code EvolvePlayerRendererMixin}, which cancels {@code PlayerRenderer#render} at
 * HEAD. Cancelling there costs the body, its layers (armour, held item, cape) and the name tag -
 * the mixin puts the name tag back. The shadow and the fire overlay are drawn by
 * {@code EntityRenderDispatcher} before {@code render} is ever called, so they survive untouched.
 *
 * <p>The transform block below is vanilla's own, copied out of
 * {@code LivingEntityRenderer#render}: entity scale, {@code YP(180 - bodyYaw)}, flip, then the
 * 1.501-block drop that puts model-space y = 24 at the feet.
 *
 * <p><b>Client only.</b>
 */
public final class PlayerRenderSwap {

    /**
     * The model is authored 4.68 blocks tall so it matches a stage-5 hitbox exactly. Scaling by
     * {@code player.getScale() * RENDER_SCALE} keeps the beast the same height as whatever body the
     * player currently has, which matters for {@code /evolve model @s on} at a lower stage.
     */
    private static final float RENDER_SCALE = 1.8F / ApexBeast.MODEL_HEIGHT;

    /** Vanilla's colour for "invisible but visible to you": white at 39/255 alpha. */
    private static final int TRANSLUCENT_TINT = 654311423;

    private static ApexBeastModel<LivingEntity> model;
    private static boolean bakeFailed;

    /** True when this player should be drawn as the beast rather than as their skin. */
    public static boolean shouldSwap(AbstractClientPlayer player) {
        if (!EvolveFeature.isReady() || player.isSpectator()) {
            return false;
        }
        EvolutionData state = ClientEvolutionCache.state(player.getUUID());
        if (state.transforming()) {
            // Mid-sequence the player keeps their own body; the beast appears when it lands.
            return false;
        }
        return state.usesBeastModel();
    }

    /**
     * Draws the beast at the player's position. The pose stack arrives positioned at the player's
     * feet, exactly as {@code PlayerRenderer#render} received it.
     */
    public static void renderBeast(AbstractClientPlayer player, float partialTicks, PoseStack poseStack,
                                   MultiBufferSource buffer, int packedLight) {
        ApexBeastModel<LivingEntity> beast = model();
        if (beast == null) {
            return;
        }

        poseStack.pushPose();

        beast.attackTime = player.getAttackAnim(partialTicks);
        beast.riding = player.isPassenger();
        beast.young = false;

        float bodyYaw = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partialTicks, player.yHeadRotO, player.yHeadRot);
        float netHeadYaw = Mth.wrapDegrees(headYaw - bodyYaw);
        float headPitch = Mth.lerp(partialTicks, player.xRotO, player.getXRot());

        float scale = player.getScale() * RENDER_SCALE;
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);

        float limbSwing = 0.0F;
        float limbSwingAmount = 0.0F;
        if (!player.isPassenger() && player.isAlive()) {
            limbSwingAmount = Math.min(1.0F, player.walkAnimation.speed(partialTicks));
            limbSwing = player.walkAnimation.position(partialTicks);
        }
        float ageInTicks = player.tickCount + partialTicks;

        beast.prepareMobModel(player, limbSwing, limbSwingAmount, partialTicks);
        beast.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        Minecraft minecraft = Minecraft.getInstance();
        boolean bodyVisible = !player.isInvisible();
        boolean translucent = !bodyVisible && !player.isInvisibleTo(minecraft.player);
        RenderType renderType = renderType(beast, bodyVisible, translucent);
        if (renderType != null) {
            VertexConsumer consumer = buffer.getBuffer(renderType);
            int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
            beast.renderToBuffer(poseStack, consumer, packedLight, overlay,
                    translucent ? TRANSLUCENT_TINT : -1);
        }

        poseStack.popPose();
    }

    /**
     * A trimmed version of {@code LivingEntityRenderer#shouldShowName} - distance, F1, camera entity
     * and invisibility. Team name-tag visibility rules are not reproduced; on a two-player recording
     * server there are no teams.
     */
    public static boolean shouldShowName(AbstractClientPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        double distanceSq = minecraft.getEntityRenderDispatcher().distanceToSqr(player);
        float limit = player.isDiscrete() ? 32.0F : 64.0F;
        if (distanceSq >= limit * limit) {
            return false;
        }
        return Minecraft.renderNames()
                && player != minecraft.getCameraEntity()
                && !player.isInvisibleTo(minecraft.player)
                && !player.isVehicle();
    }

    /** Drops the baked model, so the next frame re-bakes it. Used after a resource reload. */
    public static void invalidate() {
        model = null;
        bakeFailed = false;
    }

    private static RenderType renderType(ApexBeastModel<LivingEntity> beast, boolean bodyVisible,
                                         boolean translucent) {
        if (translucent) {
            return RenderType.itemEntityTranslucentCull(ApexBeastModel.TEXTURE);
        }
        return bodyVisible ? beast.renderType(ApexBeastModel.TEXTURE) : null;
    }

    private static ApexBeastModel<LivingEntity> model() {
        if (model == null && !bakeFailed) {
            try {
                model = new ApexBeastModel<>(
                        Minecraft.getInstance().getEntityModels().bakeLayer(ApexBeastModel.LAYER));
            } catch (RuntimeException e) {
                bakeFailed = true;
                LOG.error("[evolve] could not bake the apex beast model layer; "
                        + "stage 5 will render as the player skin", e);
            }
        }
        return model;
    }

    private PlayerRenderSwap() {
    }
}
