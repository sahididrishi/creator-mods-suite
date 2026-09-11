package dev.riftal.creator.features.evolve.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.client.render.PlayerRenderSwap;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The stage-5 model swap: draws the Apex beast instead of the player's own body.
 *
 * <p>Cancelling {@code PlayerRenderer#render} at HEAD skips the body and every render layer -
 * armour, held items, cape, elytra - which is exactly right for a creature that has none of those.
 * It also skips the name tag, because that is emitted by {@code EntityRenderer#render} at the
 * bottom of the super call, so the tag is re-emitted here. The shadow and the fire overlay are
 * drawn by {@code EntityRenderDispatcher} before {@code render} is reached and are untouched.
 *
 * <p>Client only: listed under {@code "client"} in {@code creatormods-evolve.mixins.json}.
 */
@Mixin(PlayerRenderer.class)
public abstract class EvolvePlayerRendererMixin {

    @Shadow
    protected abstract void renderNameTag(AbstractClientPlayer entity, Component displayName,
                                          PoseStack poseStack, MultiBufferSource buffer,
                                          int packedLight, float partialTick);

    @Inject(
            method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private void creator_evolve$swapInBeast(AbstractClientPlayer entity, float entityYaw, float partialTicks,
                                            PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                            CallbackInfo ci) {
        if (!CreatorMods.isEnabled(EvolveFeature.ID)) {
            return;
        }
        if (!PlayerRenderSwap.shouldSwap(entity)) {
            return;
        }
        PlayerRenderSwap.renderBeast(entity, partialTicks, poseStack, buffer, packedLight);
        if (PlayerRenderSwap.shouldShowName(entity)) {
            this.renderNameTag(entity, entity.getDisplayName(), poseStack, buffer, packedLight,
                    partialTicks);
        }
        ci.cancel();
    }
}
