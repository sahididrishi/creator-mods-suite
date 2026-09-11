package dev.riftal.creator.features.powers.effect;

import net.minecraft.world.entity.Mob;

import java.util.UUID;

/**
 * One mob held by Mob Freeze, plus the AI flags it had before, so the thaw restores the mob exactly
 * as it was found - a mob that was already {@code NoAI} (an armour-stand-like build prop, a mob
 * frozen by another feature) must stay that way.
 *
 * @param mob           the frozen mob
 * @param hadNoAi       {@code Mob#isNoAi()} before the freeze
 * @param hadNoGravity  {@code Entity#isNoGravity()} before the freeze
 * @param until         absolute game tick the freeze ends
 * @param owner         the player whose Mob Freeze did this, so a logout, a death or a
 *                      {@code /power clear} can thaw exactly that player's mobs and nobody else's
 */
public record FrozenMob(Mob mob, boolean hadNoAi, boolean hadNoGravity, long until, UUID owner) {
}
