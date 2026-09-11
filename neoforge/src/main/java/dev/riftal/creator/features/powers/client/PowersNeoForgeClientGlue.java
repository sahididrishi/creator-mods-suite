package dev.riftal.creator.features.powers.client;

import dev.riftal.creator.Constants;
import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.powers.PowersFeature;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * NeoForge-only glue for the {@code powers} feature's key mappings.
 *
 * <p>Auto-discovered by FML's {@code AutomaticEventSubscriber}, so it needs no entry in the shared
 * {@code neoforge.mods.toml}. {@code RegisterKeyMappingsEvent} is a <em>mod</em> bus event and
 * fires only on the physical client.
 *
 * <p>Guarded by the feature toggle: the class is scanned whether or not {@code powers} is enabled,
 * and a switched-off feature must not add anything to the controls screen.
 */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PowersNeoForgeClientGlue {

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        if (!CreatorMods.isEnabled(PowersFeature.ID)) {
            return;
        }
        for (KeyMapping mapping : PowerKeys.list()) {
            event.register(mapping);
        }
        PowersClient.markKeyPollingWired();
    }

    private PowersNeoForgeClientGlue() {
    }
}
