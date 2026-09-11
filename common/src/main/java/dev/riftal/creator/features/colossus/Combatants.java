package dev.riftal.creator.features.colossus;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.function.Predicate;

/**
 * Who the Colossus is allowed to touch.
 *
 * <p>Only {@code hurt()} is filtered by vanilla for creative and spectator players.
 * {@code setRemainingFireTicks}, {@code knockback} and {@code setDeltaMovement} are not: they will
 * happily set a creative player alight and shove a spectator across the arena, and
 * {@code hurtMarked} then sends the shove to their client as a real velocity packet. On this
 * project that is the second camera operator, who flies through the arena in creative for the wide
 * shot and must come out of it with a steady camera and no fire overlay.
 *
 * <p>Every AoE in this feature filters through {@link #NOT_A_CAMERA}. A creative or spectator
 * player is never a victim of the slam, the roar, the combo, the ash bombs or the ring of fire.
 */
public final class Combatants {

    /**
     * True for a player who is watching rather than fighting - creative or spectator. Never true
     * for a mob, and never true for a survival or adventure player.
     */
    public static boolean isCamera(Entity entity) {
        return entity instanceof Player player && (player.isSpectator() || player.isCreative());
    }

    /** The filter every area-of-effect in this feature passes its candidates through. */
    public static final Predicate<LivingEntity> NOT_A_CAMERA = victim -> !isCamera(victim);

    private Combatants() {
    }
}
