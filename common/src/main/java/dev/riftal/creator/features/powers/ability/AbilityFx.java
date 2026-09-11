package dev.riftal.creator.features.powers.ability;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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

    /** A unit vector pointing from {@code from} to {@code to}, or {@code fallback} if they coincide. */
    public static Vec3 direction(Vec3 from, Vec3 to, Vec3 fallback) {
        Vec3 delta = to.subtract(from);
        return delta.lengthSqr() < 1.0E-6D ? fallback : delta.normalize();
    }

    private AbilityFx() {
    }
}
