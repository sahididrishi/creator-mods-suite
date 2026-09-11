package dev.riftal.creator.features.powers.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.powers.PowersFeature;
import dev.riftal.creator.features.powers.server.PowerManager;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "This world is closing" - the signal that drops every live dome and thaws every frozen mob.
 *
 * <p>Without it the feature's static maps keep hold of {@code ServerPlayer} and {@code Mob} objects
 * from a level that has been discarded (and, through them, the whole {@code ServerLevel} graph)
 * until the <em>next</em> world load happens to wipe them - by which time the frozen mobs have been
 * written to disk with {@code NoAI} set and the thaw is writing to entities of a closed level.
 *
 * <p>The loader bootstraps do have a server-stopping event, but they are shared files no feature
 * may edit, and core's own stop hook only clears {@code TickScheduler} and silent mode. Injecting
 * into {@code stopServer} is the same route the {@code rules} feature takes.
 */
@Mixin(MinecraftServer.class)
public abstract class PowersMinecraftServerMixin {

    @Inject(method = "stopServer()V", at = @At("HEAD"))
    private void creator_powers$stopPowers(CallbackInfo ci) {
        if (!CreatorMods.isEnabled(PowersFeature.ID)) {
            return;
        }
        PowerManager.onServerStopping((MinecraftServer) (Object) this);
    }
}
