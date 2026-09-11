package dev.riftal.creator;

import net.fabricmc.api.ModInitializer;

public class CreatorModsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        CreatorMods.init();
    }
}
