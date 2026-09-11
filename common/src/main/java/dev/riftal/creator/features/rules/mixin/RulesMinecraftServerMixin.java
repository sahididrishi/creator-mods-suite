package dev.riftal.creator.features.rules.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.rules.RulesFeature;
import dev.riftal.creator.features.rules.hooks.RuleHooks;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Drives the rule engine's tick and gives it a reliable "this world just started / just stopped"
 * signal.
 *
 * <p>Core's {@code TickScheduler} is cleared on every server stop, so a repeating task registered
 * once during mod construction would not survive leaving a single-player world and loading the next
 * one. Ticking off {@code MinecraftServer} itself is the one hook that always exists.
 */
@Mixin(MinecraftServer.class)
public abstract class RulesMinecraftServerMixin {

    @Inject(method = "tickServer(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
    private void creator_rules$tickRules(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        if (!CreatorMods.isEnabled(RulesFeature.ID)) {
            return;
        }
        RuleHooks.onServerTick((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer()V", at = @At("HEAD"))
    private void creator_rules$stopRules(CallbackInfo ci) {
        if (!CreatorMods.isEnabled(RulesFeature.ID)) {
            return;
        }
        RuleHooks.onServerStopping((MinecraftServer) (Object) this);
    }
}
