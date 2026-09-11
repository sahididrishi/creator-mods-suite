package dev.riftal.creator.features.toolkit.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import dev.riftal.creator.features.toolkit.client.ClientToolkitState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code /toolkit hide nametags on} - no floating names in the frame. Client only.
 *
 * <p>Targets {@code EntityRenderer#renderNameTag} rather than {@code shouldShowName}: every vanilla
 * override of {@code renderNameTag} ends in a {@code super} call, so one injection covers players,
 * item frames and mobs alike.
 */
@Mixin(EntityRenderer.class)
public abstract class ToolkitEntityRendererMixin<T extends Entity> {

    @Inject(
            method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At("HEAD"),
            cancellable = true)
    private void creator_toolkit$hideNameTag(Entity entity, Component displayName, PoseStack poseStack,
                                             MultiBufferSource bufferSource, int packedLight,
                                             float partialTick, CallbackInfo ci) {
        if (!ToolkitFeature.enabled()) {
            return;
        }
        if (ClientToolkitState.nametagsHidden()) {
            ci.cancel();
        }
    }
}
