package dev.riftal.creator.features.evolve.progression;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.core.util.Titles;
import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.data.EvolutionData;
import dev.riftal.creator.features.evolve.net.TransformFxPayload;
import dev.riftal.creator.features.evolve.perk.StagePerks;
import dev.riftal.creator.features.evolve.stage.EvolutionStage;
import dev.riftal.creator.features.evolve.stage.StageMath;
import dev.riftal.creator.features.evolve.stage.StageModifiers;
import dev.riftal.creator.features.evolve.stage.Stages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The two-second lock, spiral and title card that sits between two stages.
 *
 * <p>Server-driven end to end. The spiral and the chime are ordinary server-spawned particles and
 * sounds, so every nearby player sees them with no client state at all; the only thing the client
 * is told is when to flash the screen and when to show "EVOLVING" on the HUD
 * ({@link TransformFxPayload}).
 *
 * <p>The player's stage is written into the attachment at tick 0, not at the end. If the player
 * dies, relogs or changes dimension mid-sequence the stage is already banked, so the heartbeat in
 * {@code EvolveServerHooks} only has to finish the job - and {@link #finish} is idempotent.
 *
 * <p><b>Two scheduler tags, deliberately.</b> A real sequence owns
 * {@code creator_evolve:transform/&lt;uuid&gt;} (the spiral <em>and</em> the finish task); the b-roll
 * {@code /evolve fx} owns {@code creator_evolve:broll/&lt;uuid&gt;} and nothing else. They were one tag
 * once, and {@code /evolve fx start} on a mid-transformation player cancelled the finish task -
 * which left the target locked at movement speed 0 with {@code transforming=true} until they
 * relogged. Keep them apart.
 */
public final class Transformation {

    /** Length of a normal stage-up, in ticks. */
    public static final int TICKS = 40;

    /** Length of the stage-5 stage-up, in ticks. Longer, because it is the money shot. */
    public static final int APEX_TICKS = 60;

    /** Particle emission period, in ticks. */
    private static final int FX_PERIOD = 2;

    /** How high the spiral climbs, in blocks, before the scale multiplier. */
    private static final double SPIRAL_HEIGHT = 2.5D;

    /** Radius of the spiral, in blocks, before the scale multiplier. */
    private static final double SPIRAL_RADIUS = 1.2D;

    /** How long the sequence into {@code targetStage} lasts. */
    public static int durationFor(int targetStage) {
        return targetStage >= Stages.MAX ? APEX_TICKS : TICKS;
    }

    /**
     * Starts the sequence. The stage is banked immediately; the body catches up when the sequence
     * finishes.
     */
    public static void begin(ServerPlayer player, int targetStage) {
        if (!EvolveFeature.isReady()) {
            return;
        }
        int target = Stages.clamp(targetStage);
        int duration = durationFor(target);
        long endTick = player.level().getGameTime() + duration;

        cancelTasks(player.getUUID());
        cancelFxTasks(player.getUUID());
        EvolutionData banked = EvolveManager.data(player).withStage(target).withTransform(true, endTick);
        EvolveManager.setData(player, banked);

        StagePerks.revokeAll(player);
        StageModifiers.lockMovement(player);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;

        Fx.sound(player.level(), player.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.PLAYERS, 1.0F, 0.7F);
        Payloads.sendToTracking(player,
                TransformFxPayload.start(player.getUUID(), duration, target));
        EvolveManager.sync(player);

        scheduleFx(player.getUUID(), duration, tagFor(player.getUUID()));
        scheduleFinish(player.getUUID(), duration);

        LOG.info("[evolve] {} is transforming into stage {} over {} ticks",
                player.getGameProfile().getName(), target, duration);
    }

    /**
     * Re-arms the fx and the finish timer for a player who was mid-sequence when they relogged or
     * changed dimension.
     */
    public static void resume(ServerPlayer player, int remainingTicks) {
        if (!EvolveFeature.isReady()) {
            return;
        }
        int remaining = Math.max(1, remainingTicks);
        EvolutionData state = EvolveManager.data(player);
        cancelTasks(player.getUUID());
        StageModifiers.lockMovement(player);
        Payloads.sendToTracking(player,
                TransformFxPayload.start(player.getUUID(), remaining, state.stage()));
        scheduleFx(player.getUUID(), remaining, tagFor(player.getUUID()));
        scheduleFinish(player.getUUID(), remaining);
    }

    /**
     * Ends the sequence: releases the lock, writes the new body, plays the roar and the title card.
     * Safe to call twice, and safe to call on a player who was never transforming.
     */
    public static void finish(ServerPlayer player) {
        if (!EvolveFeature.isReady()) {
            return;
        }
        cancelTasks(player.getUUID());
        EvolutionData state = EvolveManager.data(player);
        EvolutionStage stage = Stages.byOrdinal(state.stage());

        StageModifiers.unlockMovement(player);
        StageModifiers.apply(player, stage);
        EvolveManager.setData(player, state.withTransform(false, 0L));

        if (player.level() instanceof ServerLevel level) {
            Vec3 centre = player.position();
            Fx.particles(level, ParticleTypes.END_ROD, centre.add(0.0D, 1.0D, 0.0D), 60, 0.9D, 0.12D);
            Fx.particleRing(level, ParticleTypes.REVERSE_PORTAL, centre.add(0.0D, 0.1D, 0.0D), 1.8D, 24);
        }

        Fx.sound(player.level(), player.position(),
                stage.isFinal() ? EvolveFeature.roarApex() : EvolveFeature.roarSmall(),
                SoundSource.PLAYERS, 1.4F, stage.isFinal() ? 0.85F : 1.0F);
        Fx.sound(player.level(), player.position(), EvolveFeature.evolveComplete(),
                SoundSource.PLAYERS, 0.8F, 1.0F);

        if (player.connection != null) {
            // A player who is being disconnected this very tick has no packet listener left. One
            // NPE here used to spend the heartbeat's three-strike failure budget and take the whole
            // feature down for the rest of the session.
            Titles.show(player,
                    Component.translatable("title.creator_evolve.stage", stage.ordinal())
                            .withStyle(ChatFormatting.BOLD),
                    Component.translatable(stage.nameKey()),
                    5, 40, 10);
        }

        Payloads.sendToTracking(player, TransformFxPayload.stop(player.getUUID(), stage.ordinal()));
        if (stage.isFinal()) {
            // The beast lands roaring: every tracker's proxy plays animation.apex.roar once.
            Payloads.sendToTracking(player,
                    TransformFxPayload.roar(player.getUUID(), stage.ordinal()));
        }
        EvolveManager.sync(player);

        LOG.info("[evolve] {} is now stage {} ({})", player.getGameProfile().getName(),
                stage.ordinal(), stage.key());

        // XP earned mid-sequence is banked but cannot start a second sequence while one is running.
        // Catch up here, once: the next finish computes the same stage and stops.
        int deserved = StageMath.stageForXp(state.xp());
        if (deserved > stage.ordinal()) {
            begin(player, deserved);
        }
    }

    /**
     * Stops a running sequence without applying it. The stage banked at tick 0 is <em>left</em> in
     * the attachment and the body is <em>not</em> written, so the only two callers are
     * {@code EvolveManager.setStage} and {@code EvolveManager.reset}, both of which write the body
     * immediately afterwards. Do not add a third: {@code /evolve fx stop} used to route through
     * here and left the player's data on stage N+1 while their attributes were still stage N's.
     * B-roll cancels go through {@link #stopFxOnly} instead.
     */
    public static void abort(ServerPlayer player) {
        cancelTasks(player.getUUID());
        cancelFxTasks(player.getUUID());
        StageModifiers.unlockMovement(player);
        EvolutionData state = EvolveManager.data(player);
        if (state.transforming()) {
            EvolveManager.setData(player, state.withTransform(false, 0L));
        }
        if (EvolveFeature.isReady()) {
            Payloads.sendToTracking(player,
                    TransformFxPayload.stop(player.getUUID(), state.stage()));
        }
    }

    /**
     * Plays the sequence's fx without changing anything. For b-roll: {@code /evolve fx start}.
     *
     * <p>Refuses outright while a real transformation is running on that player: the b-roll take is
     * never worth stepping on a live sequence, and the fx would be indistinguishable anyway.
     *
     * @return true when the b-roll actually started
     */
    public static boolean playFxOnly(ServerPlayer player, int ticks) {
        if (!EvolveFeature.isReady() || EvolveManager.data(player).transforming()) {
            return false;
        }
        int duration = Math.max(1, Math.min(400, ticks));
        cancelFxTasks(player.getUUID());
        Fx.sound(player.level(), player.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.PLAYERS, 1.0F, 0.7F);
        Payloads.sendToTracking(player, TransformFxPayload.start(player.getUUID(), duration,
                EvolveManager.data(player).stage()));
        scheduleFx(player.getUUID(), duration, fxTagFor(player.getUUID()));
        TickScheduler.runLater(duration, () -> {
            ServerPlayer target = resolve(player.getUUID());
            if (target != null) {
                stopFxOnly(target);
            }
        }).tag(fxTagFor(player.getUUID()));
        return true;
    }

    /**
     * Ends a {@code /evolve fx start} take. Touches the b-roll tag and nothing else, so it is safe
     * to fire at a player who happens to be mid-transformation: their spiral, their finish task and
     * their movement lock are all on the other tag and are left exactly where they were.
     */
    public static void stopFxOnly(ServerPlayer player) {
        cancelFxTasks(player.getUUID());
        if (!EvolveFeature.isReady() || EvolveManager.data(player).transforming()) {
            // A real sequence owns the client's flash right now. Cutting it short here would blank
            // the HUD readout half way through a take.
            return;
        }
        Payloads.sendToTracking(player,
                TransformFxPayload.stop(player.getUUID(), EvolveManager.data(player).stage()));
    }

    /** Cancels every scheduled task belonging to one player's real sequence. */
    public static void cancelTasks(UUID playerId) {
        TickScheduler.cancelAll(tagFor(playerId));
    }

    /** Cancels one player's b-roll fx tasks, leaving any real sequence alone. */
    public static void cancelFxTasks(UUID playerId) {
        TickScheduler.cancelAll(fxTagFor(playerId));
    }

    private static void scheduleFinish(UUID playerId, int duration) {
        TickScheduler.runLater(duration, () -> {
            ServerPlayer player = resolve(playerId);
            if (player != null) {
                finish(player);
            }
        }).tag(tagFor(playerId));
    }

    private static void scheduleFx(UUID playerId, int duration, ResourceLocation tag) {
        int steps = Math.max(1, duration / FX_PERIOD);
        int[] step = {0};
        TickScheduler.runRepeating(FX_PERIOD, steps, task -> {
            ServerPlayer player = resolve(playerId);
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                task.cancel();
                return;
            }
            int index = step[0]++;
            float progress = (float) index / (float) steps;
            emitSpiral(level, player, index, progress);
            if (index % 3 == 0) {
                Fx.sound(level, player.position(), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, 0.6F, 0.8F + progress * 0.6F);
            }
        }).tag(tag);
    }

    private static void emitSpiral(ServerLevel level, ServerPlayer player, int index, float progress) {
        double scale = Math.max(0.5D, player.getScale());
        double radius = SPIRAL_RADIUS * scale;
        double height = SPIRAL_HEIGHT * scale * progress;
        Vec3 feet = player.position();
        for (int arm = 0; arm < 3; arm++) {
            double angle = index * 0.55D + arm * (Math.PI * 2.0D / 3.0D);
            Vec3 point = feet.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
            Fx.particles(level, ParticleTypes.PORTAL, point, 1, 0.0D, 0.0D);
            if (arm == 0) {
                Fx.particles(level, ParticleTypes.END_ROD, point, 1, 0.02D, 0.01D);
            }
        }
    }

    private static ServerPlayer resolve(UUID playerId) {
        MinecraftServer server = TickScheduler.server();
        return server == null ? null : server.getPlayerList().getPlayer(playerId);
    }

    /** Scheduler tag for a real transformation: the spiral <em>and</em> the finish task. */
    private static ResourceLocation tagFor(UUID playerId) {
        return EvolveFeature.id("transform/" + playerId);
    }

    /** Scheduler tag for {@code /evolve fx} b-roll. Never carries a finish task. */
    private static ResourceLocation fxTagFor(UUID playerId) {
        return EvolveFeature.id("broll/" + playerId);
    }

    private Transformation() {
    }
}
