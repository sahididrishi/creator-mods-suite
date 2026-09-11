package dev.riftal.creator.features.powers.ability;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Shared server-side helpers for the six abilities.
 *
 * <p>The velocity helpers exist because of the single most common "my ability does nothing" bug:
 * a velocity set on the server only reaches a client if {@code hurtMarked} is raised (which makes
 * {@code ServerEntity} broadcast a {@code ClientboundSetEntityMotionPacket} at the end of the
 * tick). For a player we also send the packet straight down their own connection so the launch is
 * felt on the same tick rather than on the next entity-tracker update.
 */
public final class AbilityFx {

    /** Applies an absolute velocity to any entity and makes sure every watching client learns it. */
    public static void launch(Entity entity, Vec3 velocity) {
        entity.setDeltaMovement(velocity);
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player && player.connection != null) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    /** Adds to an entity's velocity. Used for knockback, where existing motion should survive. */
    public static void nudge(Entity entity, Vec3 delta) {
        launch(entity, entity.getDeltaMovement().add(delta));
    }

    /**
     * Bosses are exempt from Ender Pull and Mob Freeze: yanking the ender dragon out of its flight
     * path or freezing the warden is a bug report, not a feature.
     */
    public static boolean isBoss(Entity entity) {
        EntityType<?> type = entity.getType();
        return type == EntityType.ENDER_DRAGON
                || type == EntityType.WITHER
                || type == EntityType.WARDEN
                || type == EntityType.ELDER_GUARDIAN;
    }

    /**
     * Whether one of the two damaging area abilities (Ground Pound, Fire Burst) may touch this
     * entity.
     *
     * <p>An unfiltered "every {@code LivingEntity} in the radius" is a retake: the pound launches
     * the creator's own horse across the arena, knocks over the armour stands the set is built from
     * and hits the second player who is holding the camera. So:
     *
     * <ul>
     *   <li>the caster is never their own victim;</li>
     *   <li>armour stands are scenery, not mobs;</li>
     *   <li>a pet the caster tamed is left alone (somebody else's wolf is fair game);</li>
     *   <li>players are only hit when vanilla would allow it - {@code ServerPlayer#canHarmPlayer}
     *       is the same check that covers both PvP being off and the two being on one team.</li>
     * </ul>
     *
     * <p>Bosses are <em>not</em> excluded here: the plan exempts them from Ender Pull and Mob
     * Freeze, not from damage. What they are exempt from is being thrown - see
     * {@link #isBoss(Entity)} at the knockback call sites.
     */
    public static boolean canAffect(ServerPlayer caster, Entity entity) {
        if (entity == caster || !entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        if (entity instanceof ArmorStand) {
            return false;
        }
        if (entity instanceof TamableAnimal pet && caster.getUUID().equals(pet.getOwnerUUID())) {
            return false;
        }
        return !(entity instanceof Player other) || caster.canHarmPlayer(other);
    }

    /** A unit vector pointing from {@code from} to {@code to}, or {@code fallback} if they coincide. */
    public static Vec3 direction(Vec3 from, Vec3 to, Vec3 fallback) {
        Vec3 delta = to.subtract(from);
        return delta.lengthSqr() < 1.0E-6D ? fallback : delta.normalize();
    }

    private AbilityFx() {
    }
}
