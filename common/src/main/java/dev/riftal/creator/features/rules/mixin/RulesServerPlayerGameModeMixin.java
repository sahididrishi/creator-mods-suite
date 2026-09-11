package dev.riftal.creator.features.rules.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.rules.RulesFeature;
import dev.riftal.creator.features.rules.hooks.RuleHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code blocks_explode}'s hook: a player-driven block break that actually succeeded.
 *
 * <p>The state is captured at HEAD because by RETURN the block is already air, and the rule wants to
 * know what it was. Nothing is cancelled here - the explosion is scheduled a tick later by the rule
 * so the break has fully finished first.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class RulesServerPlayerGameModeMixin {

    @Shadow
    protected ServerLevel level;

    @Shadow
    @Final
    protected ServerPlayer player;

    @Unique
    private BlockState creator_rules$brokenState;

    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"))
    private void creator_rules$captureBrokenState(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!CreatorMods.isEnabled(RulesFeature.ID)) {
            return;
        }
        this.creator_rules$brokenState = this.level.getBlockState(pos);
    }

    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z", at = @At("RETURN"))
    private void creator_rules$afterDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!CreatorMods.isEnabled(RulesFeature.ID)) {
            return;
        }
        BlockState state = this.creator_rules$brokenState;
        this.creator_rules$brokenState = null;
        if (state == null || !cir.getReturnValueZ()) {
            return;
        }
        RuleHooks.onBlockDestroyed(this.player, this.level, pos, state);
    }
}
