package dev.riftal.creator.features.evolve.event;

import dev.riftal.creator.Constants;
import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.evolve.EvolveFeature;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * NeoForge-only glue: the twin of {@code EvolveFabricHooks}.
 *
 * <p>Auto-discovered by FML's {@code AutomaticEventSubscriber}, so it needs no entry in the shared
 * {@code neoforge.mods.toml}. {@code PlayerEvent.StartTracking} is a <em>game</em> bus event (the
 * default for {@code @EventBusSubscriber}) and fires on the server when {@code getEntity()} - the
 * player - begins tracking {@code getTarget()}.
 *
 * <p>Guarded by the feature toggle: the class is scanned whether or not {@code evolve} is enabled.
 */
@EventBusSubscriber(modid = Constants.MOD_ID)
public final class EvolveNeoForgeHooks {

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!CreatorMods.isEnabled(EvolveFeature.ID)) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer tracker) {
            EvolveServerHooks.onStartTracking(tracker, event.getTarget());
        }
    }

    private EvolveNeoForgeHooks() {
    }
}
