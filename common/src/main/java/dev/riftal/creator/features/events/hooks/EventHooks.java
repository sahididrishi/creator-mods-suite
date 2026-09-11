package dev.riftal.creator.features.events.hooks;

import net.minecraft.world.entity.MobCategory;

/**
 * The narrow static surface the {@code events} mixins are allowed to touch.
 *
 * <p>Kept deliberately tiny and side-effect free: a mixin runs on a hot vanilla path, so it must be
 * able to bail out after one volatile field read when no event is up.
 */
public final class EventHooks {

    /** Entity tag put on monsters a blood moon has claimed, so the cleanup sweep can find them. */
    public static final String TAG_BLOODMOON = "creator_events_bloodmoon";

    /** Entity tag put on everything a siege spawned. */
    public static final String TAG_SIEGE = "creator_events_siege";

    private static volatile float monsterSpawnMultiplier = 1.0F;

    /**
     * How much headroom over the vanilla mob cap the active event wants for {@code category}.
     * 1.0 means "leave vanilla alone", which is the state whenever no event is running.
     */
    public static float spawnCapMultiplier(MobCategory category) {
        return category == MobCategory.MONSTER ? monsterSpawnMultiplier : 1.0F;
    }

    /** Set by {@code BloodMoonEvent} on start and reset to 1.0 on stop. */
    public static void setMonsterSpawnMultiplier(float multiplier) {
        monsterSpawnMultiplier = multiplier < 1.0F ? 1.0F : Math.min(multiplier, 8.0F);
    }

    /** True while some event has asked for extra monsters. */
    public static boolean spawnCapRaised() {
        return monsterSpawnMultiplier > 1.0F;
    }

    /** Called when the feature tears everything down, so a crash cannot leave the cap raised. */
    public static void reset() {
        monsterSpawnMultiplier = 1.0F;
    }

    private EventHooks() {
    }
}
