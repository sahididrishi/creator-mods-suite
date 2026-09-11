package dev.riftal.creator.features.arsenal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.riftal.creator.features.arsenal.ArsenalFeature;
import dev.riftal.creator.features.arsenal.entity.GrappleHookEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the grapple hook as a camera-facing quad plus a rope back to the wielder's hand.
 *
 * <p>The hand-position and rope maths follow vanilla's own {@code FishingHookRenderer}: sixteen
 * {@code RenderType.lineStrip()} segments with a slight sag, anchored to a screen-space offset in
 * first person and to the body in third. <b>Client only</b> - reachable exclusively from
 * {@code ArsenalFeature#initClient()}.
 */
public class GrappleHookRenderer extends EntityRenderer<GrappleHookEntity> {

    private static final ResourceLocation TEXTURE =
            ArsenalFeature.res("textures/entity/grapple_hook.png");
    private static final RenderType HOOK_TYPE = RenderType.entityCutout(TEXTURE);

    private static final int ROPE_SEGMENTS = 16;
    private static final int ROPE_COLOUR = 0xFF2B2B2B;
    private static final double VIEW_BOBBING_SCALE = 960.0D;

    public GrappleHookRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(GrappleHookEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        PoseStack.Pose hookPose = poseStack.last();
        VertexConsumer hookConsumer = bufferSource.getBuffer(HOOK_TYPE);
        vertex(hookConsumer, hookPose, packedLight, 0.0F, 0, 0, 1);
        vertex(hookConsumer, hookPose, packedLight, 1.0F, 0, 1, 1);
        vertex(hookConsumer, hookPose, packedLight, 1.0F, 1, 1, 0);
        vertex(hookConsumer, hookPose, packedLight, 0.0F, 1, 0, 0);
        poseStack.popPose();

        Entity owner = entity.getOwner();
        if (owner instanceof Player player) {
            float swingAnim = player.getAttackAnim(partialTick);
            float swing = Mth.sin(Mth.sqrt(swingAnim) * (float) Math.PI);
            Vec3 hand = handPosition(player, swing, partialTick);
            Vec3 hook = entity.getPosition(partialTick).add(0.0D, 0.15D, 0.0D);
            float dx = (float) (hand.x - hook.x);
            float dy = (float) (hand.y - hook.y);
            float dz = (float) (hand.z - hook.z);
            VertexConsumer ropeConsumer = bufferSource.getBuffer(RenderType.lineStrip());
            PoseStack.Pose ropePose = poseStack.last();
            for (int i = 0; i <= ROPE_SEGMENTS; i++) {
                ropeVertex(dx, dy, dz, ropeConsumer, ropePose,
                        fraction(i, ROPE_SEGMENTS), fraction(i + 1, ROPE_SEGMENTS));
            }
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private Vec3 handPosition(Player player, float swing, float partialTick) {
        int sign = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        if (this.entityRenderDispatcher.options.getCameraType().isFirstPerson()
                && player == Minecraft.getInstance().player) {
            double scale = VIEW_BOBBING_SCALE / this.entityRenderDispatcher.options.fov().get().intValue();
            Vec3 offset = this.entityRenderDispatcher.camera
                    .getNearPlane()
                    .getPointOnPlane(sign * 0.525F, -0.1F)
                    .scale(scale)
                    .yRot(swing * 0.5F)
                    .xRot(-swing * 0.7F);
            return player.getEyePosition(partialTick).add(offset);
        }

        float bodyRot = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * ((float) Math.PI / 180.0F);
        double sin = Mth.sin(bodyRot);
        double cos = Mth.cos(bodyRot);
        float scale = player.getScale();
        double side = sign * 0.35D * scale;
        double forward = 0.8D * scale;
        float crouch = player.isCrouching() ? -0.1875F : 0.0F;
        return player.getEyePosition(partialTick)
                .add(-cos * side - sin * forward, crouch - 0.45D * scale, -sin * side + cos * forward);
    }

    private static float fraction(int numerator, int denominator) {
        return (float) numerator / (float) denominator;
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int packedLight,
                               float x, int y, int u, int v) {
        consumer.addVertex(pose, x - 0.5F, (float) y - 0.5F, 0.0F)
                .setColor(-1)
                .setUv((float) u, (float) v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void ropeVertex(float x, float y, float z, VertexConsumer consumer, PoseStack.Pose pose,
                                   float fraction, float nextFraction) {
        float px = x * fraction;
        float py = y * (fraction * fraction + fraction) * 0.5F + 0.25F;
        float pz = z * fraction;
        float nx = x * nextFraction - px;
        float ny = y * (nextFraction * nextFraction + nextFraction) * 0.5F + 0.25F - py;
        float nz = z * nextFraction - pz;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 0.0F) {
            nx /= length;
            ny /= length;
            nz /= length;
        }
        consumer.addVertex(pose, px, py, pz).setColor(ROPE_COLOUR).setNormal(pose, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(GrappleHookEntity entity) {
        return TEXTURE;
    }
}
