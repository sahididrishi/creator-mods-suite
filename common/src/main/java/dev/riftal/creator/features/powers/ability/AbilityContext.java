package dev.riftal.creator.features.powers.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Everything an {@link Ability#activate(AbilityContext)} call needs. Server side only - an ability
 * never sees a client type.
 *
 * @param player   the activating player, always a live {@code ServerPlayer}
 * @param level    the level that player is in
 * @param gameTime absolute game tick of the activation, the clock every cooldown is measured on
 */
public record AbilityContext(ServerPlayer player, ServerLevel level, long gameTime) {

    /** Convenience: the player's feet position. */
    public Vec3 origin() {
        return player.position();
    }

    /** Convenience: the player's eye position, the origin of every aimed ability. */
    public Vec3 eye() {
        return player.getEyePosition();
    }

    /** Convenience: normalised look vector. */
    public Vec3 look() {
        return player.getLookAngle().normalize();
    }
}
