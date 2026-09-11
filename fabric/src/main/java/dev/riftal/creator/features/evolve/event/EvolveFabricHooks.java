package dev.riftal.creator.features.evolve.event;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.evolve.EvolveFeature;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;

/**
 * Fabric-only glue: tell a player about everyone's evolution stage the instant they come into
 * tracking range.
 *
 * <p>Called by reflection from {@code EvolveFeature#initCommon()} - the same trick
 * {@code PowersClient} uses for key mappings - because Fabric discovers entrypoints only through
 * {@code fabric.mod.json}, and no feature may edit that shared file (CONTRACT.md section 2).
 *
 * <p>{@code EntityTrackingEvents.START_TRACKING} fires just before the tracked entity's spawn
 * packet is sent, which is the earliest moment the new viewer's {@code ClientEvolutionCache} can
 * usefully be filled in: the stage is there before the first frame that could have drawn the wrong
 * body. Without it the only sync a walk-up viewer gets is the five-second roster pass, i.e. up to
 * five seconds of an Apex player rendering as a normal player.
 */
public final class EvolveFabricHooks {

    private static boolean initialised;

    /** Registers the start-tracking listener. Idempotent. */
    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;

        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
            if (!CreatorMods.isEnabled(EvolveFeature.ID)) {
                return;
            }
            EvolveServerHooks.onStartTracking(player, trackedEntity);
        });
    }

    private EvolveFabricHooks() {
    }
}
