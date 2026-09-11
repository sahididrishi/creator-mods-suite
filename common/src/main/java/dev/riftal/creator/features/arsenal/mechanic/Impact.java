package dev.riftal.creator.features.arsenal.mechanic;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Applies the Arsenal weapons' own damage <em>through</em> vanilla's post-hit invulnerability
 * window.
 *
 * <p>{@code LivingEntity#hurt} (1.21.1, line 1110) branches on the window before it does anything
 * else useful:
 *
 * <pre>{@code
 * if ((float)this.invulnerableTime > 10.0F && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
 *     if (amount <= this.lastHurt) {
 *         return false;
 *     }
 *     this.actuallyHurt(source, amount - this.lastHurt);
 * }
 * }</pre>
 *
 * <p>Two of this feature's headline mechanics land inside somebody else's window and were being
 * silently eaten by that branch:
 * <ul>
 *   <li>the Gravity Hammer's touchdown hit arrives in the <em>same</em> server tick as the fall
 *       damage its own slam caused ({@code Entity#move} -&gt; {@code checkFallDamage} runs during
 *       the entity tick, our scheduler task at the end of the same tick), so a 6.0 slam on top of
 *       4 points of fall damage used to land as 2;</li>
 *   <li>the Storm Bow's blast arrives immediately after the arrow's own hit on the entity it
 *       physically struck, so the mob you aimed at took {@code 10 - arrowDamage} while the mob
 *       standing next to it took the full 10.</li>
 * </ul>
 *
 * <p>Neither {@code minecraft:player_attack} nor {@code minecraft:lightning_bolt} is in
 * {@code minecraft:bypasses_cooldown}, and {@code LivingEntity#lastHurt} is {@code protected}, so
 * the one lever reachable from here is {@code Entity#invulnerableTime}, which is public (line 200).
 * Zeroing it sends {@code hurt} down its normal branch, which applies the full amount and then sets
 * the window back to 20 ticks itself - so the victim is not left permanently unprotected.
 */
public final class Impact {

    /**
     * Hurts {@code victim} for the full {@code amount} even if it is still inside an invulnerability
     * window from an earlier hit this tick.
     *
     * @return true if the damage was applied
     */
    public static boolean hurtThroughCooldown(LivingEntity victim, DamageSource source, float amount) {
        if (amount <= 0.0F) {
            return false;
        }
        victim.invulnerableTime = 0;
        return victim.hurt(source, amount);
    }

    private Impact() {
    }
}
