package dev.riftal.creator.features.evolve.perk.impl;

import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.perk.StagePerk;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Stage 3 - Brute. A sprinting hit lands for four extra damage and throws the target.
 *
 * <p>The plan's stage table asks for "+4 dmg and knockback 1.5" on a sprint hit, which the old
 * Strength I did not give: Strength I is +3 and applies to every swing, sprinting or not.
 *
 * <p>The damage is a transient {@code creator_evolve:charge} modifier on
 * {@code Attributes.ATTACK_DAMAGE} rather than an effect, and it is written from
 * {@link #onAttack} - which runs at the head of {@code Player#attack}, one instruction before
 * vanilla reads that attribute. So the bonus lands on <em>this</em> swing, not on the next one
 * after the five-tick heartbeat catches up. {@link #tick} only takes the modifier away again once
 * the player stops sprinting.
 *
 * <p>"+4" means +4 on the finished attribute, at every stage. The written amount is therefore
 * {@link #BONUS_DAMAGE} divided by the percentage modifiers already on the attribute - see
 * {@link #chargeAmount}, and the stage table's own {@code +50 % / +120 % / +200 %} attack bonus in
 * particular, which would otherwise multiply this bonus along with the base damage.
 */
public final class ChargePerk implements StagePerk {

    /** Fixed id of the sprint-damage modifier. */
    public static final ResourceLocation CHARGE_ID = EvolveFeature.id("charge");

    /** Flat attack damage added to the finished attack damage while sprinting. */
    public static final double BONUS_DAMAGE = 4.0D;

    /** Knockback strength applied to a sprint-hit victim, on top of vanilla's own. */
    public static final double KNOCKBACK = 1.5D;

    @Override
    public String key() {
        return "charge";
    }

    @Override
    public void tick(ServerPlayer player) {
        if (player.isSpectator() || !player.isAlive() || !player.isSprinting()) {
            clearBonus(player);
        }
    }

    @Override
    public boolean onAttack(ServerPlayer player, Entity target) {
        if (player.isSpectator() || !player.isAlive() || !player.isSprinting()) {
            clearBonus(player);
            return false;
        }
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.addOrUpdateTransientModifier(new AttributeModifier(CHARGE_ID, chargeAmount(attack),
                    AttributeModifier.Operation.ADD_VALUE));
        }
        if (target instanceof LivingEntity victim) {
            // knockback(strength, x, z) pushes the victim away from (x, z), so hand it the
            // attacker's position - exactly how RoarPerk and vanilla's own sprint knockback do it.
            victim.knockback(KNOCKBACK, player.getX() - victim.getX(), player.getZ() - victim.getZ());
        }
        return true;
    }

    @Override
    public void revoke(ServerPlayer player) {
        clearBonus(player);
    }

    /** True while the sprint bonus is written on this player. */
    public static boolean isCharged(ServerPlayer player) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        return attack != null && attack.hasModifier(CHARGE_ID);
    }

    /**
     * The {@code ADD_VALUE} amount that raises the finished attack damage by exactly
     * {@link #BONUS_DAMAGE}.
     *
     * <p>{@code AttributeInstance#calculateValue} (1.21.1) folds the modifiers as
     * {@code (base + SUM add_value) * (1 + SUM add_multiplied_base) * PRODUCT (1 +
     * add_multiplied_total)}, so every percentage modifier on the attribute multiplies an
     * {@code ADD_VALUE} amount too. {@code StageModifiers.apply} writes the stage's
     * {@code attackBonus()} as {@code ADD_MULTIPLIED_TOTAL}, so a flat 4.0 written here would land
     * as +6 on a Brute (+50 %), +8.8 on a Titan and +12 on an Apex - not the "+4 dmg" the stage
     * table and this class promise. Dividing by that factor cancels it out, whoever wrote it.
     *
     * <p>The charge modifier itself is skipped: it is {@code ADD_VALUE}, so it never contributes
     * to the factor, but leaving the previous swing's modifier out of the sum keeps this a
     * function of the other modifiers alone.
     */
    static double chargeAmount(AttributeInstance attack) {
        double multipliedBase = 0.0D;
        double multipliedTotal = 1.0D;
        for (AttributeModifier modifier : attack.getModifiers()) {
            if (modifier.id().equals(CHARGE_ID)) {
                continue;
            }
            switch (modifier.operation()) {
                case ADD_MULTIPLIED_BASE -> multipliedBase += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> multipliedTotal *= 1.0D + modifier.amount();
                default -> {
                    // ADD_VALUE shifts the result without scaling this bonus. Nothing to cancel.
                }
            }
        }
        double factor = (1.0D + multipliedBase) * multipliedTotal;
        if (factor <= 1.0E-6D) {
            // Something has pinned attack damage at (or below) zero - a -100 % modifier, say. No
            // finite ADD_VALUE amount can add damage through that, so do not divide by it.
            return BONUS_DAMAGE;
        }
        return BONUS_DAMAGE / factor;
    }

    private static void clearBonus(ServerPlayer player) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(CHARGE_ID);
        }
    }
}
