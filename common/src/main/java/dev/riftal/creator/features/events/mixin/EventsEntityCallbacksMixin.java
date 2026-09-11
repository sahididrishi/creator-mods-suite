package dev.riftal.creator.features.events.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.events.EventManager;
import dev.riftal.creator.features.events.EventsFeature;
import dev.riftal.creator.features.events.hooks.EventHooks;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Strips blood-moon glow off a monster the first time it is loaded back after the event ended.
 *
 * <p>{@code setGlowingTag} and entity tags both persist to NBT, and {@code BloodMoonEvent#onStop}
 * can only sweep the chunks that happen to be loaded at that moment. A monster tagged during the
 * night in a chunk that unloaded before {@code /event stop} otherwise stays glowing forever, and
 * the next take in that area is full of glowing zombies with no blood moon.
 *
 * <p>Clearing on load instead makes the cleanup eventually-complete regardless of which chunks were
 * loaded when the event ended, at the cost of one tag-set lookup per entity load.
 */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class EventsEntityCallbacksMixin {

    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void creator_events$clearStaleBloodMoonGlow(Entity entity, CallbackInfo ci) {
        if (!CreatorMods.isEnabled(EventsFeature.ID)) {
            return;
        }
        if ("bloodmoon".equals(EventManager.activeId())) {
            return;
        }
        if (!entity.getTags().contains(EventHooks.TAG_BLOODMOON)) {
            return;
        }
        entity.removeTag(EventHooks.TAG_BLOODMOON);
        entity.setGlowingTag(false);
    }
}
