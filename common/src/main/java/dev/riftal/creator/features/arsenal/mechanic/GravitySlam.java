package dev.riftal.creator.features.arsenal.mechanic;

import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.arsenal.ArsenalFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Gravity Hammer's lift-and-slam.
 *
 * <p>Phase one: everything living within {@link DamageMath#SLAM_RADIUS} blocks gets Levitation II
 * for 30 ticks and a kick upwards - roughly three blocks of hang time. Phase two: the levitation is
 * stripped, everything is thrown straight down hard, and fall distance is padded so even a three
 * block drop hurts. On touchdown each victim takes distance-scaled impact damage and kicks up a ring
 * of block particles.
 *
 * <p>The whole thing runs from one self-cancelling {@code TickScheduler} task, so if the player logs
 * out mid-slam the levitation is still removed on schedule and the mobs simply fall.
 */
public final class GravitySlam {

    /** Scheduler tag for every slam task. */
    public static final ResourceLocation TASK_TAG = ArsenalFeature.res("slam");

    /** Ticks the victims hang in the air before the slam. */
    public static final int LIFT_TICKS = 30;

    /** Ticks after the slam we keep watching for touchdown before giving up. */
    public static final int SLAM_WATCH_TICKS = 20;

    /** Hard cap on victims per use, so a mob farm cannot stall the server. */
    public static final int MAX_TARGETS = 32;

    /** Cooldown applied to the hammer, in ticks. */
    public static final int COOLDOWN_TICKS = 160;

    private static final Map<UUID, GravitySlam> ACTIVE = new HashMap<>();

    private final UUID ownerId;
    private final ServerLevel level;
    private final Vec3 epicentre;
    private final List<LivingEntity> lifted = new ArrayList<>();
    private final List<LivingEntity> falling = new ArrayList<>();
    private ServerPlayer owner;
    private int ticks;
    private boolean slammed;

    private GravitySlam(ServerPlayer owner, ServerLevel level, List<LivingEntity> targets) {
        this.ownerId = owner.getUUID();
        this.owner = owner;
        this.level = level;
        this.epicentre = owner.position();
        this.lifted.addAll(targets);
    }

    /**
     * Lifts everything around {@code player}. Does nothing (and returns false) if there is nothing
     * living in range - the caller still puts the hammer on cooldown so the on-camera wipe plays.
     */
    public static boolean start(ServerLevel level, ServerPlayer player) {
        cancel(player);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(DamageMath.SLAM_RADIUS),
                candidate -> candidate != player
                        && candidate.isAlive()
                        && !(candidate instanceof Player other && (other.isCreative() || other.isSpectator())));
        if (targets.size() > MAX_TARGETS) {
            targets = new ArrayList<>(targets.subList(0, MAX_TARGETS));
        }

        GravitySlam slam = new GravitySlam(player, level, targets);
        ACTIVE.put(player.getUUID(), slam);
        slam.lift();

        TickScheduler.runRepeating(1, -1, task -> {
            GravitySlam current = ACTIVE.get(slam.ownerId);
            if (current != slam) {
                task.cancel();
                return;
            }
            if (slam.tick()) {
                ACTIVE.remove(slam.ownerId);
                task.cancel();
            }
        }).tag(TASK_TAG);

        return !targets.isEmpty();
    }

    /** Drops the player's slam, removing levitation from anything still hanging. */
    public static boolean cancel(Player player) {
        GravitySlam slam = ACTIVE.remove(player.getUUID());
        if (slam == null) {
            return false;
        }
        for (LivingEntity victim : slam.lifted) {
            victim.removeEffect(MobEffects.LEVITATION);
        }
        slam.lifted.clear();
        slam.falling.clear();
        return true;
    }

    private void lift() {
        for (LivingEntity victim : this.lifted) {
            victim.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LIFT_TICKS, 1, false, false, false));
            victim.setDeltaMovement(victim.getDeltaMovement().x, 0.5D, victim.getDeltaMovement().z);
            victim.hurtMarked = true;
            pushMotion(victim);
        }
        Fx.sound(this.level, this.epicentre, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    private boolean tick() {
        this.ticks++;
        this.owner = this.level.getServer().getPlayerList().getPlayer(this.ownerId);

        if (!this.slammed) {
            if (this.ticks % 5 == 0) {
                for (LivingEntity victim : this.lifted) {
                    if (victim.isAlive()) {
                        Fx.particles(this.level, ParticleTypes.REVERSE_PORTAL,
                                victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D),
                                10, 0.35D, 0.01D);
                    }
                }
            }
            if (this.ticks >= LIFT_TICKS) {
                this.slam();
            }
            return false;
        }

        List<LivingEntity> landed = new ArrayList<>();
        for (LivingEntity victim : this.falling) {
            if (!victim.isAlive()) {
                landed.add(victim);
                continue;
            }
            if (victim.onGround() || victim.isInWater()) {
                this.impact(victim);
                landed.add(victim);
            }
        }
        this.falling.removeAll(landed);

        return this.falling.isEmpty() || this.ticks > LIFT_TICKS + SLAM_WATCH_TICKS;
    }

    private void slam() {
        this.slammed = true;
        for (LivingEntity victim : this.lifted) {
            victim.removeEffect(MobEffects.LEVITATION);
            if (!victim.isAlive()) {
                continue;
            }
            victim.setDeltaMovement(victim.getDeltaMovement().x, -1.8D, victim.getDeltaMovement().z);
            victim.hurtMarked = true;
            victim.fallDistance += 4.0F;
            pushMotion(victim);
            this.falling.add(victim);
        }
        this.lifted.clear();

        Fx.sound(this.level, this.epicentre, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.5F);
        Fx.sound(this.level, this.epicentre, ArsenalFeature.slamImpact(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private void impact(LivingEntity victim) {
        double distance = victim.position().distanceTo(this.epicentre);
        float damage = DamageMath.slamDamage(distance);
        if (damage > 0.0F) {
            DamageSource source = this.owner != null
                    ? this.level.damageSources().playerAttack(this.owner)
                    : this.level.damageSources().magic();
            victim.hurt(source, damage);
        }

        Vec3 feet = victim.position();
        BlockPos below = BlockPos.containing(feet.x, feet.y - 0.2D, feet.z);
        BlockState state = this.level.getBlockState(below);
        if (!state.isAir()) {
            BlockParticleOption crack = new BlockParticleOption(ParticleTypes.BLOCK, state);
            Fx.particleRing(this.level, crack, feet.add(0.0D, 0.1D, 0.0D), 1.2D, 20);
        }
        Fx.particles(this.level, ParticleTypes.EXPLOSION, feet.add(0.0D, 0.2D, 0.0D), 1, 0.0D, 0.0D);
    }

    private static void pushMotion(LivingEntity victim) {
        if (victim instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }
}
