package dev.riftal.creator.features.rules.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.rules.RulesFeature;
import dev.riftal.creator.features.rules.hooks.RuleHooks;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Join and respawn: the two moments when per-player rule effects have to be (re-)applied.
 *
 * <p>A respawn builds a brand-new {@code ServerPlayer}, so transient modifiers are gone; a join is
 * also where stale <em>permanent</em> modifiers left by a rule that was switched off while the
 * player was offline get stripped.
 */
@Mixin(PlayerList.class)
public abstract class RulesPlayerListMixin {

    @Inject(
            method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;"
                    + "Lnet/minecraft/server/network/CommonListenerCookie;)V",
            at = @At("TAIL"))
    private void creator_rules$onJoin(Connection connection, ServerPlayer player,
                                      CommonListenerCookie cookie, CallbackInfo ci) {
        if (!CreatorMods.isEnabled(RulesFeature.ID)) {
            return;
        }
        RuleHooks.onPlayerJoin(player);
    }

    @Inject(
            method = "respawn(Lnet/minecraft/server/level/ServerPlayer;ZLnet/minecraft/world/entity/Entity$RemovalReason;)"
                    + "Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("RETURN"))
    private void creator_rules$onRespawn(ServerPlayer player, boolean keepInventory,
                                         Entity.RemovalReason reason,
                                         CallbackInfoReturnable<ServerPlayer> cir) {
        if (!CreatorMods.isEnabled(RulesFeature.ID)) {
            return;
        }
        RuleHooks.onPlayerRespawn(cir.getReturnValue());
    }
}
