package dev.riftal.creator.features.colossus.entity.ai;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.colossus.AttackKind;
import dev.riftal.creator.features.colossus.ColossusFeature;
import dev.riftal.creator.features.colossus.entity.AshenColossusEntity;
import dev.riftal.creator.features.colossus.net.ScreenShakePayload;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

/** The 60-tick entrance: the Colossus claws its way out of the floor. Invulnerable throughout. */
public class SpawnRiseGoal extends AnimatedAttackGoal {

    public SpawnRiseGoal(AshenColossusEntity boss) {
        super(boss, AttackKind.SPAWN);
    }

    @Override
    protected void onStart(ServerLevel level) {
        Fx.sound(level, this.boss.position(), ColossusFeature.roarSound(), SoundSource.HOSTILE, 3.0F, 0.7F);
        ScreenShakePayload.sendAround(level, this.boss.position(), 0.35F, 40, 24.0F);
    }

    @Override
    protected void onTick(ServerLevel level, int tick) {
        if (tick % 4 != 0) {
            return;
        }
        BlockState floor = level.getBlockState(this.boss.blockPosition().below());
        Fx.particles(level, new BlockParticleOption(ParticleTypes.BLOCK, floor),
                this.boss.position(), 16, 1.3D, 0.06D);
        Fx.particles(level, ParticleTypes.LARGE_SMOKE, this.boss.position(), 8, 1.3D, 0.02D);
    }
}
