package dev.riftal.creator.features.toolkit;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Turns a stored dimension id back into a live level. Null when that dimension is gone. */
public final class ToolkitDimensions {

    /** The level {@code id} names on this server, or null. */
    public static ServerLevel level(MinecraftServer server, ResourceLocation id) {
        if (server == null || id == null) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        return server.getLevel(key);
    }

    private ToolkitDimensions() {
    }
}
