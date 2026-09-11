package dev.riftal.creator.features.evolve.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.event.EvolveServerHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The two gameplay hooks Evolve needs on {@code Player}, both pure side effects - nothing is
 * cancelled, nothing is redirected, and vanilla's own arithmetic is left untouched.
 *
 * <ul>
 *   <li>{@code causeFallDamage} at HEAD: landing detection for the Titan stomp. It is the one place
 *       that reliably fires the instant a player hits the ground with a known fall distance, on
 *       both loaders. The fall damage itself is left exactly as vanilla computed it, so a Titan
 *       with a raised {@code SAFE_FALL_DISTANCE} still takes (or shrugs off) the right amount.</li>
 *   <li>{@code attack} at HEAD: the Brute charge. HEAD is load-bearing here - the very first thing
 *       {@code Player#attack} does is read {@code Attributes.ATTACK_DAMAGE}, so the perk gets to
 *       write its transient modifier in time for <em>this</em> swing.</li>
 * </ul>
 */
@Mixin(Player.class)
public abstract class EvolvePlayerMixin {

    @Inject(
            method = "causeFallDamage(FFLnet/minecraft/world/damagesource/DamageSource;)Z",
            at = @At("HEAD"))
    private void creator_evolve$stompOnLanding(float fallDistance, float multiplier, DamageSource source,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!CreatorMods.isEnabled(EvolveFeature.ID)) {
            return;
        }
        EvolveServerHooks.onPlayerLanded((Player) (Object) this, fallDistance);
    }

    @Inject(
            method = "attack(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"))
    private void creator_evolve$chargeOnSprintHit(Entity target, CallbackInfo ci) {
        if (!CreatorMods.isEnabled(EvolveFeature.ID)) {
            return;
        }
        EvolveServerHooks.onPlayerAttack((Player) (Object) this, target);
    }
}
