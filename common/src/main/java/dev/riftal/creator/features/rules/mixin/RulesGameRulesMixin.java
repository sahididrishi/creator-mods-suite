package dev.riftal.creator.features.rules.mixin;

import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Opens vanilla's private {@code GameRules#register} so this feature can declare
 * {@code creator_rules.rulesHud} without a loader-specific API.
 *
 * <p>Fabric would want {@code GameRuleRegistry} and NeoForge its own access transformer; a static
 * {@link Invoker} on the vanilla method is the one spelling that is identical on both loaders and
 * needs no edit to a shared file. Nothing is injected and nothing is overwritten - the call site in
 * {@link dev.riftal.creator.features.rules.RuleGameRules} is simply redirected to the real method.
 */
@Mixin(GameRules.class)
public interface RulesGameRulesMixin {

    /**
     * {@code private static <T extends GameRules.Value<T>> GameRules.Key<T> register(String,
     * GameRules.Category, GameRules.Type<T>)} - verified in {@code GameRules.java:207}.
     */
    @Invoker("register")
    static <T extends GameRules.Value<T>> GameRules.Key<T> creator_rules$register(
            String name, GameRules.Category category, GameRules.Type<T> type) {
        throw new AssertionError("mixin did not apply");
    }
}
