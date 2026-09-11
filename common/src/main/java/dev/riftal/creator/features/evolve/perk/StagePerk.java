package dev.riftal.creator.features.evolve.perk;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.UUID;

/**
 * The passive a player carries while they stand on one stage of the ladder.
 *
 * <p>Perks are stateless singletons held by {@link StagePerks}; anything per-player lives either in
 * the player's own {@code EvolutionData} or in a small map inside the perk. All three hooks run on
 * the server thread.
 */
public interface StagePerk {

    /** Lowercase id, matching {@code EvolutionStage#perkKey()} and the {@code perk.creator_evolve.*} lang key. */
    String key();

    /**
     * Called every five ticks for every online player standing on this perk's stage. Never called
     * while a transformation is running.
     */
    default void tick(ServerPlayer player) {
    }

    /**
     * Called when the player hits the ground hard enough to take fall damage.
     *
     * @return true when the perk did something, for the caller's feedback
     */
    default boolean onLand(ServerPlayer player, float fallDistance) {
        return false;
    }

    /**
     * Called by {@code /evolve roar}. The command always plays the sound and particles; this only
     * adds the gameplay part.
     *
     * @return how many mobs were affected
     */
    default int onRoar(ServerPlayer player) {
        return 0;
    }

    /**
     * Called from the head of {@code Player#attack}, before vanilla reads
     * {@code Attributes.ATTACK_DAMAGE} - so a perk that wants to change the damage of <em>this</em>
     * swing can still write the modifier here and have it counted.
     *
     * @return true when the perk did something
     */
    default boolean onAttack(ServerPlayer player, Entity target) {
        return false;
    }

    /** Called when the player leaves this stage, so any per-player bookkeeping can be dropped. */
    default void revoke(ServerPlayer player) {
    }

    /**
     * Drops every player's bookkeeping. Called when a server starts or {@code /reload} re-arms the
     * heartbeat: a perk map that outlives a world is a slow leak and, worse, hands the next world's
     * player the previous one's half-charged state.
     */
    default void clearAll() {
    }

    /**
     * Drops the bookkeeping of everyone not in {@code onlinePlayerIds}. Called once per heartbeat,
     * so a player who logs out mid-charge does not stay in the map until the server stops.
     */
    default void retain(Set<UUID> onlinePlayerIds) {
    }
}
