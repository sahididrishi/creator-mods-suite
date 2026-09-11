package dev.riftal.creator;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.core.CreatorModsFabricBootstrap;
import net.fabricmc.api.ModInitializer;

/** Fabric main entry point. Declared in {@code fabric.mod.json} under {@code entrypoints.main}. */
public class CreatorModsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        CreatorModsFabricBootstrap.hookEvents();
        CreatorMods.init();
        CreatorModsFabricBootstrap.flushRegistries();
    }
}
