package dev.riftal.creator.features.colossus.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.colossus.ColossusFeature;
import dev.riftal.creator.features.colossus.client.ColossusScreenShake;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera shake for the slam, the phase roars and the Colossus' footsteps.
 *
 * <p>There is no vanilla or core hook for nudging the camera, and the effect is the difference
 * between "a big mob hit the ground" and "the arena moved", so this is the one mixin the feature
 * needs. It runs after {@code Camera#setup} has placed the camera and offsets it along the camera's
 * own axes; with no shake running it does nothing at all.
 *
 * <p>Client side only, and gated on the feature toggle so switching {@code colossus} off in
 * {@code config/creatormods.json} leaves vanilla camera behaviour completely untouched.
 */
@Mixin(Camera.class)
public abstract class ColossusCameraMixin {

    @Shadow
    protected abstract void move(float zoom, float dy, float dx);

    @Inject(
            method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At("TAIL"))
    private void creator_colossus$applyShake(BlockGetter level, Entity entity, boolean detached,
                                             boolean thirdPersonReverse, float partialTick,
                                             CallbackInfo ci) {
        if (!CreatorMods.isEnabled(ColossusFeature.ID)) {
            return;
        }
        if (!ColossusScreenShake.isActive()) {
            return;
        }
        this.move(ColossusScreenShake.offsetZoom(partialTick),
                ColossusScreenShake.offsetY(partialTick),
                ColossusScreenShake.offsetX(partialTick));
    }
}
