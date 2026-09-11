package dev.riftal.creator.features.powers.effect;

import net.minecraft.world.entity.Mob;

/**
 * One mob held by Mob Freeze, plus the AI flags it had before, so the thaw restores the mob exactly
 * as it was found - a mob that was already {@code NoAI} (an armour-stand-like build prop, a mob
 * frozen by another feature) must stay that way.
 *
 * @param mob           the frozen mob
 * @param hadNoAi       {@code Mob#isNoAi()} before the freeze
 * @param hadNoGravity  {@code Entity#isNoGravity()} before the freeze
 * @param until         absolute game tick the freeze ends
 */
public record FrozenMob(Mob mob, boolean hadNoAi, boolean hadNoGravity, long until) {
}
