package dev.riftal.creator.features.arsenal.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.arsenal.ArsenalFeature;
import dev.riftal.creator.features.arsenal.ArsenalRuntime;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "This world is closing" - the signal that empties the feature's four static per-player maps.
 *
 * <p>Without it a hook, a slam, a wisp or a pre-hit snapshot from the world being closed keeps that
 * world's {@code ServerLevel} reachable for the rest of the JVM's life. See {@link ArsenalRuntime}
 * for the full list. Injecting into {@code stopServer} is the same route {@code toolkit},
 * {@code powers} and {@code rules} take; the loader bootstraps do have a server-stopping event, but
 * they are shared files no feature may edit.
 */
@Mixin(MinecraftServer.class)
public abstract class ArsenalMinecraftServerMixin {

    @Inject(method = "stopServer()V", at = @At("HEAD"))
    private void creator_arsenal$dropTransientState(CallbackInfo ci) {
        if (!CreatorMods.isEnabled(ArsenalFeature.ID)) {
            return;
        }
        ArsenalRuntime.onServerStopping((MinecraftServer) (Object) this);
    }
}
