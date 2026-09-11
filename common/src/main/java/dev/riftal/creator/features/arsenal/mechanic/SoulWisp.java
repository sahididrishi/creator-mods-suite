package dev.riftal.creator.features.arsenal.mechanic;

import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.core.util.MathUtil;
import dev.riftal.creator.features.arsenal.ArsenalFeature;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The wisp a Soul Scythe kill releases.
 *
 * <p>Not an entity - it is twenty ticks of particles lerped from the corpse towards the killer's
 * eyes (re-read each tick, so it chases a moving player), and on arrival two golden absorption
 * hearts capped at six. Everything is emitted from the logical server, so every nearby client sees
 * it, not just the host.
 */
public final class SoulWisp {

    /** Scheduler tag for every wisp flight. */
    public static final ResourceLocation TASK_TAG = ArsenalFeature.res("soul_wisp");

    /**
     * Id of the {@code generic.max_absorption} headroom a soul grants its killer.
     *
     * <p>1.21 gates absorption behind that attribute, whose default value is <b>0.0</b>
     * ({@code Attributes.MAX_ABSORPTION}, a {@code RangedAttribute} with default 0), and
     * {@code LivingEntity#setAbsorptionAmount} clamps its argument to
     * {@code [0, getMaxAbsorption()]}. Without a modifier the wisp would land and grant exactly
     * nothing. Vanilla's own Absorption effect works the same way - it carries a
     * {@code minecraft:effect.absorption} modifier on this attribute - so this is the supported
     * route, not a workaround.
     */
    public static final ResourceLocation HEADROOM_ID = ArsenalFeature.res("soul_absorption");

    /** Ticks the wisp takes to reach its killer. */
    public static final int FLIGHT_TICKS = 20;

    /** Launches a wisp from {@code origin} towards {@code killer}. */
    public static void launch(ServerLevel level, Vec3 origin, ServerPlayer killer) {
        MinecraftServer server = killer.server;
        UUID killerId = killer.getUUID();

        Fx.sound(level, origin, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.7F, 1.4F);

        int[] tick = {0};
        TickScheduler.runRepeating(1, FLIGHT_TICKS, task -> {
            ServerPlayer player = server.getPlayerList().getPlayer(killerId);
            if (player == null || !player.isAlive() || player.level() != level) {
                task.cancel();
                return;
            }
            tick[0]++;
            float progress = Math.min(1.0F, tick[0] / (float) FLIGHT_TICKS);
            Vec3 target = player.getEyePosition();
            double x = MathUtil.lerp(progress, origin.x, target.x);
            double y = MathUtil.lerp(progress, origin.y, target.y)
                    + Math.sin(progress * Math.PI) * 0.75D;
            double z = MathUtil.lerp(progress, origin.z, target.z);
            Fx.particles(level, ParticleTypes.SOUL, new Vec3(x, y, z), 3, 0.05D, 0.01D);

            if (tick[0] >= FLIGHT_TICKS) {
                arrive(level, player);
            }
        }).tag(TASK_TAG);
    }

    private static void arrive(ServerLevel level, ServerPlayer player) {
        grantHeadroom(player);
        player.setAbsorptionAmount(DamageMath.absorptionAfterKill(player.getAbsorptionAmount()));
        Fx.particles(level, ParticleTypes.SOUL, player.getEyePosition(), 8, 0.25D, 0.01D);
        Fx.sound(level, player.position(), ArsenalFeature.soulAbsorb(), SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    /**
     * Raises the killer's absorption ceiling to {@link DamageMath#ABSORPTION_CAP} so the hearts the
     * wisp is about to hand over survive {@code setAbsorptionAmount}'s clamp. See
     * {@link #HEADROOM_ID}. The modifier is transient: it is never written to the player file, so a
     * creator's world is not permanently edited by having used the scythe once.
     */
    private static void grantHeadroom(ServerPlayer player) {
        AttributeInstance ceiling = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (ceiling == null || ceiling.hasModifier(HEADROOM_ID)) {
            return;
        }
        ceiling.addTransientModifier(new AttributeModifier(HEADROOM_ID, DamageMath.ABSORPTION_CAP,
                AttributeModifier.Operation.ADD_VALUE));
    }

    private SoulWisp() {
    }
}
