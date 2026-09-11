package dev.riftal.creator;

import dev.riftal.creator.core.CreatorMods;
import net.fabricmc.api.DedicatedServerModInitializer;

/** Fabric dedicated-server entry point. Declared under {@code entrypoints.server}. */
public class CreatorModsFabricServer implements DedicatedServerModInitializer {

    @Override
    public void onInitializeServer() {
        CreatorMods.initServer();
    }
}
