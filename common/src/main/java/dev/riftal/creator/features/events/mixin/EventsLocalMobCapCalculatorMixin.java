package dev.riftal.creator.features.events.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.events.EventsFeature;
import dev.riftal.creator.features.events.hooks.EventHooks;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the <em>per-chunk</em> mob cap while a blood moon is up, so the horde actually thickens
 * around the camera instead of only in the band between the global vanilla cap and the raised one.
 *
 * <p>Vanilla (verified in {@code LocalMobCapCalculator.MobCounts#canSpawn}):
 * {@code return this.counts.getOrDefault(category, 0) < category.getMaxInstancesPerChunk();}
 * This raises the right-hand side by the event's multiplier and nothing else - when the raised
 * condition fails, the vanilla condition fails too, so the injection simply falls through and
 * vanilla returns {@code false} on its own.
 *
 * <p>{@code MobCounts} is a package-private nested class, hence {@code targets = }; priority 900
 * matches {@link EventsSpawnStateMixin} and the plan's note about Enhanced Celestials sharing these
 * targets.
 *
 * <p><b>Guard order</b> is the same deal as {@link EventsSpawnStateMixin}: the plain field read
 * that ends the idle case comes first, the enabled-guard still precedes the side effect, and
 * {@code chunkSpawnMultiplier} can only be raised at all after {@link EventsNaturalSpawnerMixin}
 * has checked the feature is on.
 */
@Mixin(targets = "net.minecraft.world.level.LocalMobCapCalculator$MobCounts", priority = 900)
public abstract class EventsLocalMobCapCalculatorMixin {

    @Shadow
    @Final
    private Object2IntMap<MobCategory> counts;

    @Inject(
            method = "canSpawn(Lnet/minecraft/world/entity/MobCategory;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void creator_events$raiseLocalCap(MobCategory category,
                                              CallbackInfoReturnable<Boolean> cir) {
        float multiplier = EventHooks.chunkSpawnMultiplier(category);
        if (multiplier <= 1.0F) {
            return;
        }
        if (!CreatorMods.isEnabled(EventsFeature.ID)) {
            return;
        }
        int raisedCap = (int) (category.getMaxInstancesPerChunk() * multiplier);
        if (this.counts.getOrDefault(category, 0) < raisedCap) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
