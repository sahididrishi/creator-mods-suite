package dev.riftal.creator.features.arsenal.mechanic;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.arsenal.entity.GrappleHookEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * One player being reeled towards one hook.
 *
 * <p>Straight-line approach: every tick the remaining distance is divided by the remaining ticks
 * and clamped, so the player always arrives inside {@link DamageMath#GRAPPLE_PULL_TICKS} ticks
 * without ever exceeding {@link DamageMath#GRAPPLE_MAX_SPEED} blocks per tick. Fall damage is
 * suppressed for the whole flight and for a short grace window after it, otherwise landing lower
 * than you started still hurts.
 */
public final class GrapplePull {

    /** Ticks of fall-damage immunity after the pull ends. */
    public static final int SAFE_LANDING_TICKS = 10;

    private final GrappleHookEntity hook;
    private int ticksLeft = DamageMath.GRAPPLE_PULL_TICKS;
    private int safeLandingLeft = SAFE_LANDING_TICKS;
    private boolean pulling = true;

    GrapplePull(GrappleHookEntity hook) {
        this.hook = hook;
    }

    /** The hook this pull is anchored to. */
    public GrappleHookEntity hook() {
        return this.hook;
    }

    /** True while velocity is still being applied (as opposed to the safe-landing tail). */
    public boolean isPulling() {
        return this.pulling;
    }

    /**
     * Advances one server tick.
     *
     * @return true when the pull is completely finished and its task may be cancelled
     */
    public boolean tick(ServerPlayer player) {
        if (this.hook.isRemoved() && this.pulling) {
            this.pulling = false;
        }

        if (this.pulling) {
            Vec3 target = this.hook.anchor().add(0.0D, 0.5D, 0.0D);
            Vec3 toAnchor = target.subtract(player.position());
            if (toAnchor.length() < DamageMath.GRAPPLE_ARRIVAL_DISTANCE || this.ticksLeft <= 0) {
                this.pulling = false;
            } else {
                Vec3 velocity = DamageMath.grappleVelocity(toAnchor, this.ticksLeft);
                player.setDeltaMovement(velocity);
                player.hurtMarked = true;
                player.fallDistance = 0.0F;
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                this.ticksLeft--;
                if (player.level() instanceof ServerLevel serverLevel) {
                    Fx.particles(serverLevel, ParticleTypes.CLOUD, player.position(), 2, 0.1D, 0.0D);
                }
                return false;
            }
        }

        // Tail: keep the landing soft for a moment after the player stops being dragged.
        player.fallDistance = 0.0F;
        this.safeLandingLeft--;
        return this.safeLandingLeft <= 0;
    }

    /** Ends the velocity phase immediately, keeping the safe-landing tail. */
    public void stopPulling() {
        this.pulling = false;
    }
}
