package dev.riftal.creator.features.events.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.events.EventsFeature;
import dev.riftal.creator.features.events.client.ClientEventState;
import dev.riftal.creator.features.events.client.EventSoundLoop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client half of the director's lifecycle: a fixed-rate tick for the tint fade and the ambient
 * loop, and a hard reset when the player leaves a world.
 *
 * <p>The reset is the important one. {@code ClientEventState} is process-wide static and the server
 * broadcasts nothing while idle, so without this, quitting to the title mid blood moon and opening
 * <em>any</em> other world left the sky red and {@code BLOOD MOON until dawn} on the HUD until some
 * event in the new world happened to start and stop.
 *
 * <p>{@code Minecraft#disconnect(Screen, boolean)} is the single funnel for every way a session
 * ends - quit to title, kick, connection error - because
 * {@code ClientCommonPacketListenerImpl#onDisconnect} routes into it (line 294 of that class), and
 * the two shorter {@code disconnect} overloads delegate to it.
 *
 * <p>Client only: listed under {@code "client"} in {@code creatormods-events.mixins.json}.
 */
@Mixin(Minecraft.class)
public abstract class EventsMinecraftMixin {

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void creator_events$tickClientState(CallbackInfo ci) {
        if (!CreatorMods.isEnabled(EventsFeature.ID)) {
            return;
        }
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.level == null || minecraft.player == null) {
            ClientEventState.clientTick(Double.NaN, "");
        } else {
            ClientEventState.clientTick(minecraft.player.getY(),
                    minecraft.level.dimension().location().toString());
        }
        EventSoundLoop.tick(minecraft);
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
    private void creator_events$clearOnDisconnect(Screen nextScreen, boolean keepResourcePacks,
                                                  CallbackInfo ci) {
        if (!CreatorMods.isEnabled(EventsFeature.ID)) {
            return;
        }
        EventSoundLoop.stop((Minecraft) (Object) this);
        ClientEventState.reset();
    }
}
