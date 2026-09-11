package dev.riftal.creator.features.powers.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.powers.PowersFeature;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes Shield Dome and the dash i-frame window actually stop damage.
 *
 * <p>There is no loader-neutral damage hook in core, and the two loaders spell theirs differently
 * ({@code LivingIncomingDamageEvent} on NeoForge, {@code ServerLivingEntityEvents.ALLOW_DAMAGE} on
 * Fabric), so this is the one place the feature genuinely needs a mixin.
 *
 * <p>{@code ServerPlayer#hurt} is the outermost override for a player on the logical server, so a
 * cancel here happens before any of vanilla's own bookkeeping (sleep interruption, shield
 * handling, combat tracker) has run. The decision itself lives in
 * {@link PowersFeature#shouldCancelDamage(ServerPlayer, DamageSource)} - this class is a
 * delegation and nothing else, so the behaviour stays unit-reviewable in ordinary Java.
 */
@Mixin(ServerPlayer.class)
public abstract class PowersServerPlayerMixin {

    @Inject(
            method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void creator_powers$absorbWithDome(DamageSource source, float amount,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!CreatorMods.isEnabled(PowersFeature.ID)) {
            return;
        }
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!PowersFeature.shouldCancelDamage(self, source)) {
            return;
        }
        cir.setReturnValue(false);
    }
}
