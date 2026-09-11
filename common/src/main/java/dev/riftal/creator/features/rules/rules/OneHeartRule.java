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
 * <b>Minecraft but you have one heart.</b>
 *
 * <p>-18 on {@code generic.max_health}, re-applied on join and on respawn. The modifier is
 * transient, so it is never written to player NBT: a player who was offline when the rule was
 * switched off comes back on twenty hearts rather than stuck on one. {@link #stripFrom} covers the
 * mid-session case - {@code RuleManager} calls it on join for every rule that is <em>not</em>
 * active.
 *
 * <p>Composes with {@code hearts_currency}: both are modifiers on the same attribute, and the shop
 * refuses any purchase that would leave the player under one heart.
 */
public final class OneHeartRule implements Rule {

    /** {@code ADD_VALUE} on max health: 20 -> 2. */
    public static final double MAX_HEALTH_PENALTY = -18.0D;

    @Override
    public String id() {
        return "one_heart";
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
            RuleAttributes.heal(player);
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

    /** Applies the cap and pulls current health inside it. */
    public static void applyTo(LivingEntity entity) {
        RuleAttributes.apply(entity, Attributes.MAX_HEALTH, RuleIds.ONE_HEART,
                MAX_HEALTH_PENALTY, AttributeModifier.Operation.ADD_VALUE);
        RuleAttributes.clampHealth(entity);
    }

    /** Removes the cap. */
    public static void removeFrom(LivingEntity entity) {
        RuleAttributes.remove(entity, Attributes.MAX_HEALTH, RuleIds.ONE_HEART);
    }

    @Override
    public void stripFrom(ServerPlayer player) {
        removeFrom(player);
    }
}
