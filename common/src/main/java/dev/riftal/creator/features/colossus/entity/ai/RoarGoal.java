package dev.riftal.creator.features.colossus.entity.ai;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.core.util.Selection;
import dev.riftal.creator.features.colossus.AttackKind;
import dev.riftal.creator.features.colossus.entity.AshenColossusEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The phase-change roar. The boss cannot be hurt while it plays (see
 * {@link AttackKind#isInvulnerable()}), and everything close by is shoved back on the hit tick.
 */
public class RoarGoal extends AnimatedAttackGoal {

    private static final double PUSH_RADIUS = 10.0D;
    private static final double PUSH_STRENGTH = 0.9D;

    public RoarGoal(AshenColossusEntity boss) {
        super(boss, AttackKind.ROAR);
    }

    @Override
    protected void onHit(ServerLevel level, int index) {
        Vec3 centre = this.boss.position();
        for (LivingEntity victim : Selection.livingAround(level, centre, PUSH_RADIUS, this.boss)) {
            if (this.boss.isOwnMinion(victim)) {
                continue;
            }
            victim.knockback(PUSH_STRENGTH, centre.x - victim.getX(), centre.z - victim.getZ());
        }
        Fx.particles(level, ParticleTypes.LARGE_SMOKE,
                centre.add(0.0D, this.boss.getBbHeight() * 0.7D, 0.0D), 40, 1.8D, 0.08D);
        Fx.particleRing(level, ParticleTypes.ASH, centre.add(0.0D, 0.2D, 0.0D), 4.0D, 32);
    }

    @Override
    protected void onTick(ServerLevel level, int tick) {
        if (tick < AttackKind.ROAR.hitTick(0)) {
            this.faceTarget();
        }
    }
}
