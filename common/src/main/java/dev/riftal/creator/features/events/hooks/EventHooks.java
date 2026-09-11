package dev.riftal.creator.features.events.hooks;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

/**
 * The narrow static surface the {@code events} mixins are allowed to touch.
 *
 * <p>Kept deliberately tiny and side-effect free: a mixin runs on a hot vanilla path, so it must be
 * able to bail out after one field read when no event is up.
 *
 * <p>The spawn-cap raise is scoped to the level the event is anchored to. {@code
 * NaturalSpawner.SpawnState} carries no pointer back to its level, so the level is latched once per
 * chunk by {@code EventsNaturalSpawnerMixin} in {@link #beginChunkSpawn} and read back by the two
 * per-category mixins through {@link #chunkSpawnMultiplier}. Everything in that path runs on the
 * server thread inside a single {@code NaturalSpawner#spawnForChunk} call.
 */
public final class EventHooks {

    /** Entity tag put on monsters a blood moon has claimed, so the cleanup sweep can find them. */
    public static final String TAG_BLOODMOON = "creator_events_bloodmoon";

    /** Entity tag put on everything a siege spawned. */
    public static final String TAG_SIEGE = "creator_events_siege";

    private static volatile float monsterSpawnMultiplier = 1.0F;
    private static volatile ResourceKey<Level> spawnDimension;

    /** Server thread only: the multiplier in force for the level currently being spawned. */
    private static float chunkMultiplier = 1.0F;

    /**
     * How much headroom over the vanilla mob cap the active event wants for {@code category}, in
     * the event's own dimension. 1.0 means "leave vanilla alone".
     */
    public static float spawnCapMultiplier(MobCategory category) {
        return category == MobCategory.MONSTER ? monsterSpawnMultiplier : 1.0F;
    }

    /** Set by {@code BloodMoonEvent} on start and reset to 1.0 on stop. */
    public static void setMonsterSpawnMultiplier(float multiplier, ResourceKey<Level> dimension) {
        monsterSpawnMultiplier = multiplier < 1.0F ? 1.0F : Math.min(multiplier, 8.0F);
        spawnDimension = monsterSpawnMultiplier > 1.0F ? dimension : null;
        if (monsterSpawnMultiplier <= 1.0F) {
            chunkMultiplier = 1.0F;
        }
    }

    /** True while some event has asked for extra monsters somewhere. */
    public static boolean spawnCapRaised() {
        return monsterSpawnMultiplier > 1.0F;
    }

    /** The dimension the raise applies to, or null when nothing is raised. */
    public static ResourceKey<Level> spawnDimension() {
        return spawnDimension;
    }

    /**
     * Latches the multiplier for one {@code NaturalSpawner#spawnForChunk} call. Called from the
     * spawner mixin, which is where the {@code ServerLevel} is still in scope.
     */
    public static void beginChunkSpawn(ResourceKey<Level> dimension) {
        float multiplier = monsterSpawnMultiplier;
        ResourceKey<Level> raised = spawnDimension;
        chunkMultiplier = multiplier > 1.0F && raised != null && raised.equals(dimension)
                ? multiplier
                : 1.0F;
    }

    /**
     * The multiplier for the chunk being spawned right now - one plain field read plus an enum
     * compare, which is the whole point of latching it in {@link #beginChunkSpawn}.
     */
    public static float chunkSpawnMultiplier(MobCategory category) {
        return category == MobCategory.MONSTER ? chunkMultiplier : 1.0F;
    }

    /** Called when the feature tears everything down, so a crash cannot leave the cap raised. */
    public static void reset() {
        monsterSpawnMultiplier = 1.0F;
        spawnDimension = null;
        chunkMultiplier = 1.0F;
    }

    private EventHooks() {
    }
}
