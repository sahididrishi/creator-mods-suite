package dev.riftal.creator.features.evolve.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.event.EvolveServerHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Landing detection for the Titan stomp.
 *
 * <p>{@code Player#causeFallDamage} is the one place that reliably fires the instant a player hits
 * the ground with a known fall distance, on both loaders. The injection is a side effect only: the
 * fall damage itself is left exactly as vanilla computed it, so a Titan with a raised
 * {@code SAFE_FALL_DISTANCE} still takes (or shrugs off) the right amount.
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
}
