package dev.riftal.creator;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.core.CreatorModsFabricClientBootstrap;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client entry point. Declared in {@code fabric.mod.json} under {@code entrypoints.client}. */
public class CreatorModsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CreatorMods.initClient();
        CreatorModsFabricClientBootstrap.flush();
    }
}
