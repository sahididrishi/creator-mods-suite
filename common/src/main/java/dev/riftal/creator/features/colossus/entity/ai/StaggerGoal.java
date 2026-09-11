package dev.riftal.creator.features.colossus.entity.ai;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.colossus.AttackKind;
import dev.riftal.creator.features.colossus.ColossusFeature;
import dev.riftal.creator.features.colossus.entity.AshenColossusEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;

/**
 * The recording interrupt: {@code /colossus stagger} drops whatever the boss was doing and leaves
 * it reeling for 40 ticks, taking double damage. Useful for lining up a hero shot.
 */
public class StaggerGoal extends AnimatedAttackGoal {

    public StaggerGoal(AshenColossusEntity boss) {
        super(boss, AttackKind.STAGGER);
    }

    @Override
    protected void onStart(ServerLevel level) {
        this.boss.getNavigation().stop();
        Fx.sound(level, this.boss.position(), ColossusFeature.hurtSound(), SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    @Override
    protected void onTick(ServerLevel level, int tick) {
        if (tick % 5 == 0) {
            Fx.particles(level, ParticleTypes.CRIT,
                    this.boss.position().add(0.0D, this.boss.getBbHeight() * 0.6D, 0.0D),
                    6, 1.1D, 0.03D);
        }
    }
}
