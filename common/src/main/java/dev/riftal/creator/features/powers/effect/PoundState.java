package dev.riftal.creator.features.powers.effect;

import net.minecraft.server.level.ServerPlayer;

/**
 * A Ground Pound in flight.
 *
 * @param player    the pounding player, held directly so the landing check costs one field read
 * @param slamming  true once the downward phase has started; while false the player is still being
 *                  popped upwards and an early landing must not trigger the shockwave
 * @param expiresAt absolute game tick after which the pound is abandoned (fell into water, the
 *                  void, or got stuck) so the player is never left in a pounding state forever
 */
public record PoundState(ServerPlayer player, boolean slamming, long expiresAt) {
}
