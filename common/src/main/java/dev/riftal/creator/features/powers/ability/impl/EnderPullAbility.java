package dev.riftal.creator.features.powers.ability.impl;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.powers.PowersFeature;
import dev.riftal.creator.features.powers.ability.Ability;
import dev.riftal.creator.features.powers.ability.AbilityContext;
import dev.riftal.creator.features.powers.ability.AbilityFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Slot 4. Rips whatever is under the crosshair - up to 20 blocks away - to the player's feet.
 *
 * <p>Velocity is capped at 2.2 so a distant target lands on the creator instead of sailing past,
 * and a constant {@code +0.35} of lift carries the target over one-block lips on the way in.
 */
public final class EnderPullAbility implements Ability {

    private static final double RANGE = 20.0D;
    private static final double PULL_PER_BLOCK = 0.18D;
    private static final double MAX_PULL = 2.2D;
    private static final double LIFT = 0.35D;
    private static final int BEAM_POINTS = 20;

    @Override
    public String path() {
        return "ender_pull";
    }

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(PowersFeature.NAMESPACE, path());
    }

    @Override
    public int cooldownTicks() {
        return 120;
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        return findTarget(player) != null;
    }

    @Override
    public void activate(AbilityContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = context.level();

        LivingEntity target = findTarget(player);
        if (target == null) {
            return;
        }

        Vec3 pull = player.position().subtract(target.position());
        double distance = pull.length();
        Vec3 direction = distance < 1.0E-4D ? new Vec3(0.0D, 1.0D, 0.0D) : pull.normalize();
        double speed = Math.min(distance * PULL_PER_BLOCK, MAX_PULL);
        AbilityFx.launch(target, direction.scale(speed).add(0.0D, LIFT, 0.0D));

        drawBeam(level, player.getEyePosition(),
                target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D));
        Fx.particles(level, ParticleTypes.PORTAL,
                target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), 24, 0.4D, 0.2D);
        Fx.sound(level, player.position(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.8F);
        Fx.sound(level, target.position(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 1.2F);
    }

    /**
     * The first living, non-boss, non-spectator entity on the look ray. Shared by {@code canUse}
     * and {@code activate} so a keypress that cannot find a target never spends the cooldown.
     */
    public static LivingEntity findTarget(ServerPlayer player) {
        Vec3 from = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 to = from.add(look.scale(RANGE));
        AABB search = player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0D);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, from, to, search,
                EnderPullAbility::isPullable, RANGE * RANGE);
        if (hit == null) {
            return null;
        }
        Entity entity = hit.getEntity();
        return entity instanceof LivingEntity living ? living : null;
    }

    private static boolean isPullable(Entity entity) {
        return entity instanceof LivingEntity
                && entity.isAlive()
                && !entity.isSpectator()
                && !AbilityFx.isBoss(entity);
    }

    private static void drawBeam(ServerLevel level, Vec3 from, Vec3 to) {
        for (int i = 0; i <= BEAM_POINTS; i++) {
            double t = i / (double) BEAM_POINTS;
            Vec3 point = from.add(to.subtract(from).scale(t));
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, point.x, point.y, point.z, 1,
                    0.02D, 0.02D, 0.02D, 0.0D);
        }
    }

    @Override
    public int hudColor() {
        return 0xFFB57FFF;
    }
}
