package dev.riftal.creator;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class CreatorModsNeoForge {

    public CreatorModsNeoForge(IEventBus eventBus) {
        CreatorMods.init();
    }
}
