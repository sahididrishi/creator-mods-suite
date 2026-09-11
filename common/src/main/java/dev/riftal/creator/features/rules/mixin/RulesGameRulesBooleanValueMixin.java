package dev.riftal.creator.features.rules.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.BiConsumer;

/**
 * The other half of {@link RulesGameRulesMixin}: {@code GameRules.BooleanValue#create} is
 * package-private in {@code net.minecraft.world.level}, so a boolean rule type cannot be built from
 * our package without this.
 *
 * <p>The two-argument overload is used because the change listener is what lets a pack maker type
 * {@code /gamerule creator_rules.rulesHud false} and have every client's HUD update on the spot.
 */
@Mixin(GameRules.BooleanValue.class)
public interface RulesGameRulesBooleanValueMixin {

    /**
     * {@code static GameRules.Type<GameRules.BooleanValue> create(boolean, BiConsumer<MinecraftServer,
     * GameRules.BooleanValue>)} - verified in {@code GameRules.java:279}.
     */
    @Invoker("create")
    static GameRules.Type<GameRules.BooleanValue> creator_rules$create(
            boolean defaultValue, BiConsumer<MinecraftServer, GameRules.BooleanValue> changeListener) {
        throw new AssertionError("mixin did not apply");
    }
}
