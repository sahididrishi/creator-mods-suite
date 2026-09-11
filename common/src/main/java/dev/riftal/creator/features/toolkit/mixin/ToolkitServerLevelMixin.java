package dev.riftal.creator.features.toolkit.mixin;

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
 *
 * <p>Both halves of vanilla's entity tick are covered. {@code tickNonPassenger} ends with a loop
 * over the entity's passengers, so cancelling it at HEAD also stops a spider jockey - but a mob
 * riding something that is <em>not</em> a mob (a boat, a minecart, an armour stand) is reached only
 * through {@code tickPassenger}, which our cancel never sees. Without the second injection
 * {@code /toolkit freeze mobs on} halts every mob on the ground while the villager in the boat
 * keeps rowing.
 *
 * <p>Both guards open with {@link ToolkitFeature#enabled()}, a plain static boolean read. This runs
 * once per entity per tick: {@code CreatorMods.isEnabled(id)} would allocate a stream pipeline and a
 * capturing lambda every time, for a value that cannot change while the game is running.
 */
@Mixin(ServerLevel.class)
public abstract class ToolkitServerLevelMixin {

    @Inject(
            method = "tickNonPassenger(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void creator_toolkit$freezeMobs(Entity entity, CallbackInfo ci) {
        if (!ToolkitFeature.enabled()) {
            return;
        }
        if (FreezeManager.isFrozen(entity)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "tickPassenger(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void creator_toolkit$freezeRidingMobs(Entity ridingEntity, Entity passengerEntity,
                                                  CallbackInfo ci) {
        if (!ToolkitFeature.enabled()) {
            return;
        }
        // A removed or mismatched passenger still has to reach vanilla's stopRiding() branch, or
        // it stays welded to a vehicle it is no longer part of.
        if (passengerEntity.isRemoved() || passengerEntity.getVehicle() != ridingEntity) {
            return;
        }
        if (FreezeManager.isFrozen(passengerEntity)) {
            ci.cancel();
        }
    }
}
