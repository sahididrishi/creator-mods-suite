package dev.riftal.creator.features.events.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.events.EventsFeature;
import dev.riftal.creator.features.events.hooks.EventHooks;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the <em>global</em> natural monster cap while a blood moon is up. The per-chunk local cap
 * is raised by {@link EventsLocalMobCapCalculatorMixin}; both are needed, or the extra monsters only
 * appear in the narrow band between the vanilla cap and the raised one.
 *
 * <p>Vanilla: {@code int i = category.getMaxInstancesPerChunk() * spawnableChunkCount / 289; return
 * mobCategoryCounts.getInt(category) >= i ? false : localMobCapCalculator.canSpawn(category, pos);}
 * (verified in {@code NaturalSpawner.SpawnState#canSpawnForCategory}). This mixin only ever
 * <em>adds</em> headroom between the vanilla cap and the raised cap; below the vanilla cap it does
 * not interfere.
 *
 * <p>Uses only the two public accessors of {@code SpawnState}, so it does not depend on the private
 * field shapes, and it is a pure addition - other mods that touch the same method still run.
 *
 * <p><b>Guard order.</b> This method runs once per spawnable chunk per category per tick - low
 * thousands of calls a tick - so the first statement is the one plain field read that ends the
 * idle case. {@code EventHooks#chunkSpawnMultiplier} can only be above 1.0 after
 * {@link EventsNaturalSpawnerMixin} has already checked the feature is enabled, and the explicit
 * enabled-guard below still precedes the only side effect this method has.
 */
@Mixin(value = NaturalSpawner.SpawnState.class, priority = 900)
public abstract class EventsSpawnStateMixin {

    @Shadow
    public abstract int getSpawnableChunkCount();

    @Shadow
    public abstract Object2IntMap<MobCategory> getMobCategoryCounts();

    @Inject(
            method = "canSpawnForCategory(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/world/level/ChunkPos;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void creator_events$raiseSpawnCap(MobCategory category, ChunkPos pos,
                                              CallbackInfoReturnable<Boolean> cir) {
        float multiplier = EventHooks.chunkSpawnMultiplier(category);
        if (multiplier <= 1.0F) {
            return;
        }
        if (!CreatorMods.isEnabled(EventsFeature.ID)) {
            return;
        }
        // 289 is 17^2, the literal value of the package-private NaturalSpawner.MAGIC_NUMBER the
        // vanilla line divides by. It cannot be @Shadow-ed: it lives on the outer class.
        int vanillaCap = category.getMaxInstancesPerChunk() * this.getSpawnableChunkCount() / 289;
        int current = this.getMobCategoryCounts().getInt(category);
        if (current >= vanillaCap && current < (int) (vanillaCap * multiplier)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
