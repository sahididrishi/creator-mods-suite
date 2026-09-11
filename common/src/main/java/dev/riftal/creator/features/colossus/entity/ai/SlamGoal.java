package dev.riftal.creator.features.colossus.entity.ai;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.colossus.AttackKind;
import dev.riftal.creator.features.colossus.ColossusFeature;
import dev.riftal.creator.features.colossus.Shockwave;
import dev.riftal.creator.features.colossus.entity.AshenColossusEntity;
import dev.riftal.creator.features.colossus.net.ScreenShakePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 1's signature move: a two-handed ground slam that throws an expanding ring of cracked
 * floor outwards. Each victim is hit exactly once, however long the ring takes to reach them.
 */
public class SlamGoal extends AnimatedAttackGoal {

    private final Set<UUID> alreadyHit = new HashSet<>();
    private int ticksSinceImpact = -1;

    public SlamGoal(AshenColossusEntity boss) {
        super(boss, AttackKind.SLAM);
    }

    @Override
    public void start() {
        super.start();
        this.alreadyHit.clear();
        this.ticksSinceImpact = -1;
    }

    @Override
    protected void onStart(ServerLevel level) {
        Fx.sound(level, this.boss.position(), ColossusFeature.swingSound(), SoundSource.HOSTILE, 2.0F, 0.8F);
    }

    @Override
    protected void onHit(ServerLevel level, int index) {
        this.ticksSinceImpact = 0;
        Fx.sound(level, this.boss.position(), ColossusFeature.slamSound(), SoundSource.HOSTILE, 3.0F, 0.9F);
        ScreenShakePayload.sendAround(level, this.boss.position(), 0.6F, 12, 24.0F);
    }

    @Override
    protected void onTick(ServerLevel level, int tick) {
        if (this.ticksSinceImpact < 0) {
            this.faceTarget();
            return;
        }
        this.ticksSinceImpact++;
        if (this.ticksSinceImpact <= Shockwave.EXPANSION_TICKS) {
            this.boss.emitSlamRing(level, this.ticksSinceImpact, this.alreadyHit);
        }
    }

    @Override
    protected void onStop(ServerLevel level) {
        this.alreadyHit.clear();
        this.ticksSinceImpact = -1;
    }
}
