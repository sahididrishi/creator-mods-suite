package dev.riftal.creator.features.colossus.entity.ai;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.colossus.AttackKind;
import dev.riftal.creator.features.colossus.ColossusFeature;
import dev.riftal.creator.features.colossus.entity.AshenColossusEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;

/**
 * Phase 2+: three Ashen Minions claw out of the arena floor. The global minion cap is enforced by
 * {@code AshenColossusEntity#summonMinions}, so a boss that is already swarmed summons fewer.
 */
public class SummonMinionsGoal extends AnimatedAttackGoal {

    public SummonMinionsGoal(AshenColossusEntity boss) {
        super(boss, AttackKind.SUMMON);
    }

    @Override
    protected void onHit(ServerLevel level, int index) {
        this.boss.summonMinions(level);
        Fx.sound(level, this.boss.position(), ColossusFeature.roarSound(), SoundSource.HOSTILE, 2.0F, 1.3F);
    }

    @Override
    protected void onTick(ServerLevel level, int tick) {
        if (tick % 3 == 0 && tick < AttackKind.SUMMON.hitTick(0)) {
            Fx.particles(level, ParticleTypes.SOUL_FIRE_FLAME,
                    this.boss.position().add(0.0D, this.boss.getBbHeight() * 0.8D, 0.0D),
                    6, 1.0D, 0.02D);
        }
    }
}
