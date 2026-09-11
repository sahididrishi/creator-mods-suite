package dev.riftal.creator.features.toolkit.mixin;

import dev.riftal.creator.features.toolkit.ToolkitFeature;
import dev.riftal.creator.features.toolkit.ToolkitRuntime;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Closes a take cleanly when the world is closed mid-shoot: the log gets a
 * {@code reason=server_stop} footer instead of just ending, and the in-memory freeze flags do not
 * leak into the next world loaded in the same JVM.
 */
@Mixin(MinecraftServer.class)
public abstract class ToolkitMinecraftServerMixin {

    @Inject(method = "stopServer()V", at = @At("HEAD"))
    private void creator_toolkit$flushTake(CallbackInfo ci) {
        if (!ToolkitFeature.enabled()) {
            return;
        }
        ToolkitRuntime.onServerStopping((MinecraftServer) (Object) this);
    }
}
