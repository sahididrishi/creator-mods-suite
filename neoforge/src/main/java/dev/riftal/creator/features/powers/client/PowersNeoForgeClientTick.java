package dev.riftal.creator.features.powers.client;

import dev.riftal.creator.Constants;
import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.powers.PowersFeature;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Per-tick key polling on NeoForge. {@code ClientTickEvent.Post} is a <em>game</em> bus event, so
 * it needs its own {@code @EventBusSubscriber} - the bus is chosen per class, not per method.
 *
 * <p>Polling happens once per client tick with {@code consumeClick()}, never per frame and never
 * with {@code isDown()}: a held key must fire exactly once.
 */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class PowersNeoForgeClientTick {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!CreatorMods.isEnabled(PowersFeature.ID)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        PowerKeys.poll();
    }

    private PowersNeoForgeClientTick() {
    }
}
