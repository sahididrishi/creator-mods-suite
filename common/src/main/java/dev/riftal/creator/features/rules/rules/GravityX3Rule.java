package dev.riftal.creator.features.rules.rules;

import dev.riftal.creator.features.rules.RuleIds;
import dev.riftal.creator.features.rules.api.Rule;
import dev.riftal.creator.features.rules.api.RuleContext;
import dev.riftal.creator.features.rules.util.RuleAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * <b>Minecraft but gravity is three times stronger.</b>
 *
 * <p>{@code generic.gravity} 0.08 to 0.24, so a jump barely clears a block and a fall is a drop.
 * {@code generic.safe_fall_distance} goes from 3 to 2 at the same time, so the three-block ledge in
 * the demo actually hurts.
 *
 * <p>Both modifiers are <em>transient</em>: they are never written to player NBT, which is exactly
 * what we want for a rule that can be switched off while a player is offline. The price is that
 * they must be re-applied on join and on respawn (a respawn builds a brand-new {@code ServerPlayer}).
 */
public final class GravityX3Rule implements Rule {

    /** {@code ADD_MULTIPLIED_TOTAL} on gravity: 0.08 -> 0.24. */
    public static final double GRAVITY_MULTIPLIER = 2.0D;

    /** {@code ADD_VALUE} on safe fall distance: 3.0 -> 2.0. */
    public static final double SAFE_FALL_BONUS = -1.0D;

    @Override
    public String id() {
        return "gravity_x3";
    }

    @Override
    public void onEnable(RuleContext ctx) {
        for (ServerPlayer player : ctx.players()) {
            applyTo(player);
        }
    }

    @Override
    public void onDisable(RuleContext ctx) {
        for (ServerPlayer player : ctx.players()) {
            stripFrom(player);
        }
    }

    @Override
    public void onPlayerJoin(RuleContext ctx, ServerPlayer player) {
        applyTo(player);
    }

    @Override
    public void onPlayerRespawn(RuleContext ctx, ServerPlayer player) {
        applyTo(player);
    }

    /** Applies both modifiers. Public so the GameTest can drive it without a player list. */
    public static void applyTo(LivingEntity entity) {
        RuleAttributes.apply(entity, Attributes.GRAVITY, RuleIds.GRAVITY,
                GRAVITY_MULTIPLIER, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        RuleAttributes.apply(entity, Attributes.SAFE_FALL_DISTANCE, RuleIds.SAFE_FALL,
                SAFE_FALL_BONUS, AttributeModifier.Operation.ADD_VALUE);
    }

    /** Takes both modifiers back off. */
    public static void removeFrom(LivingEntity entity) {
        RuleAttributes.remove(entity, Attributes.GRAVITY, RuleIds.GRAVITY);
        RuleAttributes.remove(entity, Attributes.SAFE_FALL_DISTANCE, RuleIds.SAFE_FALL);
    }

    @Override
    public void stripFrom(ServerPlayer player) {
        removeFrom(player);
    }
}
