package dev.riftal.creator.features.powers.ability.impl;

import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.powers.PowersFeature;
import dev.riftal.creator.features.powers.ability.Ability;
import dev.riftal.creator.features.powers.ability.AbilityContext;
import dev.riftal.creator.features.powers.ability.AbilityFx;
import dev.riftal.creator.features.powers.data.CooldownMath;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Slot 2. A 7 m / 70 degree cone of flame in front of the player: everything caught takes 6 damage,
 * burns for 4 seconds and is shoved backwards.
 *
 * <p>Damage is attributed to the player through {@code indirectMagic} so kills still credit the
 * creator (advancements, XP, kill feed) without the projectile bookkeeping a real fireball needs.
 */
public final class FireBurstAbility implements Ability {

    /** Cone half-angle, 35 degrees either side of the look vector. */
    private static final double COS_HALF_ANGLE = Math.cos(Math.toRadians(35.0D));
    private static final double RANGE = 7.0D;
    private static final float DAMAGE = 6.0F;
    private static final float BURN_SECONDS = 4.0F;
    private static final double KNOCKBACK = 0.6D;

    @Override
    public String path() {
        return "fire_burst";
    }

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(PowersFeature.NAMESPACE, path());
    }

    @Override
    public int cooldownTicks() {
        return 100;
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        return !player.isUnderWater();
    }

    @Override
    public void activate(AbilityContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = context.level();
        Vec3 eye = context.eye();
        Vec3 look = context.look();

        List<Entity> candidates = level.getEntities(player, player.getBoundingBox().inflate(RANGE),
                entity -> entity instanceof LivingEntity && AbilityFx.canAffect(player, entity));

        for (Entity entity : candidates) {
            Vec3 centre = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
            Vec3 toTarget = centre.subtract(eye);
            double distance = toTarget.length();
            if (distance > RANGE) {
                continue;
            }
            if (!CooldownMath.inCone(look.x, look.y, look.z,
                    toTarget.x, toTarget.y, toTarget.z, COS_HALF_ANGLE)) {
                continue;
            }
            LivingEntity victim = (LivingEntity) entity;
            victim.hurt(level.damageSources().indirectMagic(player, player), DAMAGE);
            victim.igniteForSeconds(BURN_SECONDS);
            if (!AbilityFx.isBoss(victim)) {
                Vec3 push = AbilityFx.direction(eye, centre, look).scale(KNOCKBACK);
                AbilityFx.nudge(victim, new Vec3(push.x, 0.25D, push.z));
            }
        }

        Fx.sound(level, player.position(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.9F);
        Fx.sound(level, player.position(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8F, 1.2F);

        // Paint the cone over three ticks so it reads as a sweep rather than a single pop.
        TickScheduler.runRepeating(1, 3, task -> {
            if (player.isRemoved() || !(player.level() instanceof ServerLevel current)) {
                task.cancel();
                return;
            }
            spray(current, player.getEyePosition(), player.getLookAngle().normalize());
        }).tag(PowersFeature.taskTag());
    }

    private static void spray(ServerLevel level, Vec3 eye, Vec3 look) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < 40; i++) {
            Vec3 point = conePoint(random, eye, look);
            level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 1,
                    0.05D, 0.05D, 0.05D, 0.01D);
        }
        for (int i = 0; i < 10; i++) {
            Vec3 point = conePoint(random, eye, look);
            level.sendParticles(ParticleTypes.SMALL_FLAME, point.x, point.y, point.z, 1,
                    0.05D, 0.05D, 0.05D, 0.01D);
        }
    }

    private static Vec3 conePoint(RandomSource random, Vec3 eye, Vec3 look) {
        double distance = 0.6D + random.nextDouble() * (RANGE - 0.6D);
        double spread = Math.tan(Math.toRadians(35.0D)) * distance;
        Vec3 side = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize();
        Vec3 up = side.cross(look).normalize();
        double a = (random.nextDouble() * 2.0D - 1.0D) * spread;
        double b = (random.nextDouble() * 2.0D - 1.0D) * spread;
        return eye.add(look.scale(distance)).add(side.scale(a)).add(up.scale(b));
    }

    @Override
    public int hudColor() {
        return 0xFFFF9040;
    }
}
