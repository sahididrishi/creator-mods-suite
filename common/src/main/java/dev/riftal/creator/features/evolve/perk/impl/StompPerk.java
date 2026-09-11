package dev.riftal.creator.features.evolve.perk.impl;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.core.util.Selection;
import dev.riftal.creator.features.evolve.perk.StagePerk;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Stage 4 - Titan. Landing from three blocks or more cracks the ground: everything within three
 * blocks takes six damage and gets knocked away.
 */
public final class StompPerk implements StagePerk {

    /** Minimum fall distance, in blocks, that triggers a stomp. */
    public static final float MIN_FALL = 3.0F;

    /** Radius of the shockwave, in blocks. */
    public static final double RADIUS = 3.0D;

    /** Damage dealt to everything caught in the shockwave. */
    public static final float DAMAGE = 6.0F;

    @Override
    public String key() {
        return "stomp";
    }

    @Override
    public boolean onLand(ServerPlayer player, float fallDistance) {
        if (fallDistance < MIN_FALL || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 centre = player.position();
        List<LivingEntity> caught = Selection.livingAround(level, centre, RADIUS, player);
        for (LivingEntity victim : caught) {
            victim.hurt(level.damageSources().playerAttack(player), DAMAGE);
            victim.knockback(0.9D, centre.x - victim.getX(), centre.z - victim.getZ());
        }

        Fx.particleRing(level, ParticleTypes.CLOUD, centre.add(0.0D, 0.1D, 0.0D), RADIUS, 24);
        Fx.particles(level, ParticleTypes.CRIT, centre.add(0.0D, 0.2D, 0.0D), 25, 1.2D, 0.05D);
        Fx.sound(level, centre, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9F, 0.6F);
        return true;
    }
}
