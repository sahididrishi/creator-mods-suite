package dev.riftal.creator.features.events.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.events.EventManager;
import dev.riftal.creator.features.events.EventsFeature;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The join hook {@code WorldEvent#onPlayerJoin} was written against and never wired to.
 *
 * <p>Two things happen here: the running event gets to hand out whatever is per-player (the blood
 * moon's glow, the siege boss bar), and the joining client is sent the current state immediately
 * instead of waiting up to a second for the next broadcast - which is the difference between
 * relogging mid blood moon and seeing a red sky, and relogging and seeing a vanilla one that turns
 * red a second later.
 */
@Mixin(PlayerList.class)
public abstract class EventsPlayerListMixin {

    @Inject(
            method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;"
                    + "Lnet/minecraft/server/network/CommonListenerCookie;)V",
            at = @At("TAIL"))
    private void creator_events$onJoin(Connection connection, ServerPlayer player,
                                       CommonListenerCookie cookie, CallbackInfo ci) {
        if (!CreatorMods.isEnabled(EventsFeature.ID)) {
            return;
        }
        EventManager.onPlayerJoin(player);
    }
}
