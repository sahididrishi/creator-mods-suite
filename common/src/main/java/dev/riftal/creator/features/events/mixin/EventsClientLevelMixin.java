package dev.riftal.creator.features.events.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.events.EventsFeature;
import dev.riftal.creator.features.events.client.ClientEventState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lerps the sky colour toward the active event's tint - the blood moon flush and the void's black.
 *
 * <p>Return-value modification rather than an overwrite, so shader packs and other sky mods keep
 * working; when no event is running the vanilla colour is returned untouched.
 *
 * <p>Client only: listed under {@code "client"} in {@code creatormods-events.mixins.json}.
 */
@Mixin(ClientLevel.class)
public abstract class EventsClientLevelMixin {

    @ModifyReturnValue(
            method = "getSkyColor(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"))
    private Vec3 creator_events$tintSky(Vec3 original) {
        if (!CreatorMods.isEnabled(EventsFeature.ID)) {
            return original;
        }
        return ClientEventState.tintColour(original);
    }
}
