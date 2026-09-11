package dev.riftal.creator;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.core.CreatorModsNeoForgeBootstrap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** NeoForge entry point. All three lifecycle phases are driven from the bootstrap. */
@Mod(Constants.MOD_ID)
public class CreatorModsNeoForge {

    public CreatorModsNeoForge(IEventBus modBus) {
        CreatorModsNeoForgeBootstrap.hookEvents(modBus);
        CreatorMods.init();
    }
}
