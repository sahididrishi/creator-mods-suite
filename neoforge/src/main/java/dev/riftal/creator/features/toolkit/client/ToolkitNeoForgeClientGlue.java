package dev.riftal.creator.features.toolkit.client;

import dev.riftal.creator.Constants;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * NeoForge-only glue for the {@code toolkit} feature's key mapping.
 *
 * <p>Auto-discovered by FML's {@code AutomaticEventSubscriber}, so it needs no entry in the shared
 * {@code neoforge.mods.toml}. {@code RegisterKeyMappingsEvent} is a <em>mod</em> bus event and
 * fires only on the physical client; NeoForge rejects key-mapping registration after client setup,
 * so this is the only window.
 *
 * <p>Guarded by the feature toggle: the class is scanned whether or not {@code toolkit} is enabled,
 * and a switched-off feature must not add anything to the controls screen.
 */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ToolkitNeoForgeClientGlue {

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        if (!ToolkitFeature.enabled()) {
            return;
        }
        for (KeyMapping mapping : ToolkitKeys.list()) {
            event.register(mapping);
        }
    }

    private ToolkitNeoForgeClientGlue() {
    }
}
