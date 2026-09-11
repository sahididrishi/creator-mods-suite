package dev.riftal.creator.features.toolkit.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import dev.riftal.creator.features.toolkit.freeze.FreezeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code /toolkit freeze mobs on} - a true pause.
 *
 * <p>Cancelling the entity's whole server tick stops AI, gravity, age, fire, despawn timers and the
 * walk cycle in one place, and nothing has to be written to the entity to undo it. Players, item
 * entities, projectiles and any mob a player is riding are never frozen; see
 * {@link FreezeManager#isFrozen(Entity)}.
 */
@Mixin(ServerLevel.class)
public abstract class ToolkitServerLevelMixin {

    @Inject(
            method = "tickNonPassenger(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void creator_toolkit$freezeMobs(Entity entity, CallbackInfo ci) {
        if (!CreatorMods.isEnabled(ToolkitFeature.ID)) {
            return;
        }
        if (FreezeManager.isFrozen(entity)) {
            ci.cancel();
        }
    }
}
