package dev.riftal.creator.features.powers.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.powers.PowersFeature;
import dev.riftal.creator.features.powers.server.PowerManager;
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
 * The three player-lifecycle moments this feature cannot do without: join, respawn and logout.
 *
 * <p>Core exposes no loader-neutral player event, and the two loaders spell theirs differently
 * ({@code ServerPlayerEvents.LEAVE} / {@code AFTER_RESPAWN} on Fabric, {@code PlayerLoggedOutEvent}
 * / {@code PlayerEvent.Clone} on NeoForge), so the hook lives here - the same shape the
 * {@code rules} feature uses.
 *
 * <ul>
 *   <li><b>Join</b> pushes the {@code SyncPowersPayload} on the tick the player lands, so the
 *       cooldown row is on screen on the first frame rather than whenever the next resync sweep
 *       happens to come round (plan 03 section 9: "HUD populated within 1 tick of spawn").</li>
 *   <li><b>Respawn</b> builds a brand-new {@code ServerPlayer}: the client's HUD has to be rebuilt,
 *       and the dead player's dome, pound and frozen mobs have to be handed back.</li>
 *   <li><b>Logout</b> is injected at HEAD, ahead of {@code PlayerList#save}: the dome's absorption
 *       hearts <em>are</em> written to the player file, so they have to come off before the save,
 *       not after it.</li>
 * </ul>
 */
@Mixin(PlayerList.class)
public abstract class PowersPlayerListMixin {

    @Inject(
            method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;"
                    + "Lnet/minecraft/server/network/CommonListenerCookie;)V",
            at = @At("TAIL"))
    private void creator_powers$onJoin(Connection connection, ServerPlayer player,
                                       CommonListenerCookie cookie, CallbackInfo ci) {
        if (!CreatorMods.isEnabled(PowersFeature.ID)) {
            return;
        }
        PowerManager.onPlayerJoin(player);
    }

    @Inject(
            method = "respawn(Lnet/minecraft/server/level/ServerPlayer;ZLnet/minecraft/world/entity/Entity$RemovalReason;)"
                    + "Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("RETURN"))
    private void creator_powers$onRespawn(ServerPlayer player, boolean keepInventory,
                                          Entity.RemovalReason reason,
                                          CallbackInfoReturnable<ServerPlayer> cir) {
        if (!CreatorMods.isEnabled(PowersFeature.ID)) {
            return;
        }
        PowerManager.onPlayerRespawn(player, cir.getReturnValue());
    }

    @Inject(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"))
    private void creator_powers$onLogout(ServerPlayer player, CallbackInfo ci) {
        if (!CreatorMods.isEnabled(PowersFeature.ID)) {
            return;
        }
        PowerManager.onPlayerLogout(player);
    }
}
