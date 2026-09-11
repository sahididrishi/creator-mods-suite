package dev.riftal.creator.features.events.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.events.EventsFeature;
import dev.riftal.creator.features.events.hooks.EventHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Latches "which level is being spawned right now" so the two per-category spawn mixins can scope
 * the blood moon's raised cap to the dimension the event is actually anchored to.
 *
 * <p>{@code NaturalSpawner.SpawnState} keeps no reference to its level and neither does
 * {@code LocalMobCapCalculator.MobCounts}, but {@code spawnForChunk} - the only caller of
 * {@code SpawnState#canSpawnForCategory} (line 111 of {@code NaturalSpawner}) - still has the
 * {@code ServerLevel} in hand. Without this, a blood moon started in the Overworld doubled the
 * monster cap in the Nether and the End as well.
 *
 * <p>Purely additive: nothing is cancelled, nothing is returned, and the idle path is a single
 * volatile compare per chunk.
 */
@Mixin(NaturalSpawner.class)
public abstract class EventsNaturalSpawnerMixin {

    @Inject(
            method = "spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;"
                    + "Lnet/minecraft/world/level/NaturalSpawner$SpawnState;ZZZ)V",
            at = @At("HEAD"))
    private static void creator_events$latchSpawnLevel(ServerLevel level, LevelChunk chunk,
                                                       NaturalSpawner.SpawnState spawnState,
                                                       boolean spawnFriendlies, boolean spawnMonsters,
                                                       boolean forceSpawn, CallbackInfo ci) {
        // The cheap read first: with no event up this is one volatile float compare, which is what
        // the rest of the spawn path is allowed to cost. The feature's enabled-guard follows it and
        // still precedes the only thing this method changes.
        if (!EventHooks.spawnCapRaised()) {
            return;
        }
        if (!CreatorMods.isEnabled(EventsFeature.ID)) {
            return;
        }
        EventHooks.beginChunkSpawn(level.dimension());
    }
}
