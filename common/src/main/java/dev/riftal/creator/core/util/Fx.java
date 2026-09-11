package dev.riftal.creator.core.util;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Particle and sound emission, server side. */
public final class Fx {

    /**
     * Spawns particles for every player who can see them.
     *
     * @param spread gaussian offset per axis, in blocks
     * @param speed  vanilla "speed" argument; 0 for static particles
     */
    public static void particles(ServerLevel level, ParticleOptions particle, Vec3 pos,
                                 int count, double spread, double speed) {
        level.sendParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }

    /** A ring of particles on the horizontal plane - arena edges, shockwaves, telegraphs. */
    public static void particleRing(ServerLevel level, ParticleOptions particle, Vec3 centre,
                                    double radius, int points) {
        for (Vec3 point : MathUtil.ring(centre, radius, points)) {
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** Plays a sound at a position for everyone in range. */
    public static void sound(Level level, Vec3 pos, SoundEvent sound, SoundSource source,
                             float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, source, volume, pitch);
    }

    /** Plays a hostile-category sound - the default for boss and ability audio. */
    public static void sound(Level level, Vec3 pos, SoundEvent sound) {
        sound(level, pos, sound, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    private Fx() {
    }
}
