package dev.riftal.creator.features.rules.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.rules.RulesFeature;
import dev.riftal.creator.features.rules.hooks.RuleHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code no_stop_moving}'s dimension hook, which the plan lists as a failure mode to handle
 * deliberately.
 *
 * <p>A portal drops the player somewhere else entirely and freezes them for a moment while the new
 * chunks arrive; without grace the stillness counter can put them straight into the damage loop
 * through no fault of their own. {@code changeDimension} also covers a same-dimension teleport
 * (vanilla's early branch), which deserves the same grace.
 */
@Mixin(ServerPlayer.class)
public abstract class RulesServerPlayerMixin {

    @Inject(
            method = "changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)"
                    + "Lnet/minecraft/world/entity/Entity;",
            at = @At("RETURN"))
    private void creator_rules$afterChangeDimension(DimensionTransition transition,
                                                    CallbackInfoReturnable<Entity> cir) {
        if (!CreatorMods.isEnabled(RulesFeature.ID)) {
            return;
        }
        if (cir.getReturnValue() instanceof ServerPlayer player) {
            RuleHooks.onPlayerChangedDimension(player);
        }
    }
}
