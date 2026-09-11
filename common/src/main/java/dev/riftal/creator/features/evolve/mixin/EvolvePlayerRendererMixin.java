package dev.riftal.creator.features.evolve.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.client.render.PlayerRenderSwap;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The stage-5 model swap: draws the Apex beast instead of the player's own body.
 *
 * <p>The injection wraps the single {@code super.render(...)} call inside
 * {@code PlayerRenderer#render} with a condition, which is the woodwalkers pattern the plan asks
 * for (section 4 and section 11.6) rather than cancelling the whole method at HEAD. Verified
 * against the real bytecode - {@code PlayerRenderer#render} is exactly
 * {@code setModelProperties(entity); super.render(...);}, and that {@code invokespecial} targets
 * {@code LivingEntityRenderer.render(LivingEntity, F, F, PoseStack, MultiBufferSource, I)V}.
 *
 * <p>Why it matters, given that both approaches skip the same body: cancelling at HEAD also
 * swallows every other mod's injections into {@code PlayerRenderer#render}, because the method
 * simply returns. Wrapping the inner call leaves their HEAD, TAIL and RETURN injections running and
 * only removes the one instruction we actually object to, which is what keeps 3D Skin Layers and
 * Ears - the compatibility targets the plan names - working at stages 1 to 4 and sane at 5. The
 * priority of 900 puts this mixin ahead of theirs, as the plan specifies.
 *
 * <p>The name tag goes with the body either way: it is emitted by {@code EntityRenderer#render} at
 * the bottom of the call we are skipping, so it is re-emitted here. The shadow and the fire overlay
 * are drawn by {@code EntityRenderDispatcher} before {@code render} is reached and are untouched.
 *
 * <p>Client only: listed under {@code "client"} in {@code creatormods-evolve.mixins.json}.
 */
@Mixin(value = PlayerRenderer.class, priority = 900)
public abstract class EvolvePlayerRendererMixin {

    @Shadow
    protected abstract void renderNameTag(AbstractClientPlayer entity, Component displayName,
                                          PoseStack poseStack, MultiBufferSource buffer,
                                          int packedLight, float partialTick);

    @WrapWithCondition(
            method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;"
                            + "render(Lnet/minecraft/world/entity/LivingEntity;FF"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private boolean creator_evolve$swapInBeast(LivingEntityRenderer<?, ?> instance, LivingEntity entity,
                                               float entityYaw, float partialTicks, PoseStack poseStack,
                                               MultiBufferSource buffer, int packedLight) {
        if (!CreatorMods.isEnabled(EvolveFeature.ID)) {
            return true;
        }
        if (!(entity instanceof AbstractClientPlayer player) || !PlayerRenderSwap.shouldSwap(player)) {
            return true;
        }
        PlayerRenderSwap.renderBeast(player, partialTicks, poseStack, buffer, packedLight);
        if (PlayerRenderSwap.shouldShowName(player)) {
            this.renderNameTag(player, player.getDisplayName(), poseStack, buffer, packedLight,
                    partialTicks);
        }
        return false;
    }
}
