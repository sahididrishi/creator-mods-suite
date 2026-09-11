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

import java.util.HashMap;
import java.util.Map;
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

    /** Scheduler tag for every wisp flight, so {@link #reset()} can wipe them all at once. */
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

    /**
     * Last game tick each killer played a wisp sound on, keyed by player.
     *
     * <p>One scythe swing through a pack launches three or four wisps in the same tick, from
     * corpses a block or two apart. Four {@code SOUL_ESCAPE} at effectively one point is four times
     * the amplitude - it clips - and twenty ticks later all four {@code soul_absorb} land on the
     * same tick at the same player and clip again. The wisps themselves still all fly; only the
     * duplicate sound in a tick is dropped.
     */
    private static final Map<UUID, Long> LAST_ESCAPE = new HashMap<>();
    private static final Map<UUID, Long> LAST_ABSORB = new HashMap<>();

    /** Launches a wisp from {@code origin} towards {@code killer}. */
    public static void launch(ServerLevel level, Vec3 origin, ServerPlayer killer) {
        MinecraftServer server = killer.server;
        UUID killerId = killer.getUUID();

        if (firstThisTick(LAST_ESCAPE, killerId, level.getGameTime())) {
            Fx.sound(level, origin, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.7F, 1.4F);
        }

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
        if (firstThisTick(LAST_ABSORB, player.getUUID(), level.getGameTime())) {
            Fx.sound(level, player.position(), ArsenalFeature.soulAbsorb(), SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }

    /** True the first time {@code player} asks on game tick {@code now}; false for repeats. */
    private static boolean firstThisTick(Map<UUID, Long> seen, UUID player, long now) {
        Long last = seen.put(player, now);
        return last == null || last != now;
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

    /**
     * Drops every wisp in flight and the per-player sound bookkeeping. Called from
     * {@link dev.riftal.creator.features.arsenal.ArsenalRuntime} when the server stops - a wisp
     * task captures the {@code ServerLevel} it was launched in.
     */
    public static void reset() {
        LAST_ESCAPE.clear();
        LAST_ABSORB.clear();
        TickScheduler.cancelAll(TASK_TAG);
    }

    private SoulWisp() {
    }
}
