package dev.riftal.creator.features.colossus.entity.ai;

import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.features.colossus.AttackKind;
import dev.riftal.creator.features.colossus.ColossusFeature;
import dev.riftal.creator.features.colossus.entity.AshenColossusEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 2+: the Colossus throws both arms up and a volley of ash bombs arcs down around whoever it
 * is fighting. The volley is spread over {@value #VOLLEY_SPACING_TICKS}-tick intervals with the
 * shared {@link TickScheduler} rather than a per-tick loop, and re-aims at the live target on each
 * bomb so a player who runs still gets rained on.
 */
public class LavaRainGoal extends AnimatedAttackGoal {

    /** Ticks between bombs in a volley. */
    public static final int VOLLEY_SPACING_TICKS = 2;

    /** Scheduler tag, so {@code /colossus kill} can cancel a volley mid-flight. */
    public static final ResourceLocation VOLLEY_TAG =
            ResourceLocation.fromNamespaceAndPath(ColossusFeature.NAMESPACE, "lava_rain");

    public LavaRainGoal(AshenColossusEntity boss) {
        super(boss, AttackKind.LAVA_RAIN);
    }

    @Override
    protected void onHit(ServerLevel level, int index) {
        Vec3 fallback = this.aimPoint();
        TickScheduler.runRepeating(VOLLEY_SPACING_TICKS, AshenColossusEntity.LAVA_RAIN_BOMBS, () -> {
            if (!this.boss.isAlive() || !(this.boss.level() instanceof ServerLevel current)) {
                return;
            }
            LivingEntity target = this.boss.getTarget();
            Vec3 aim = target != null && target.isAlive() ? target.position() : fallback;
            this.boss.throwAshBomb(current, aim);
        }).tag(VOLLEY_TAG);
    }

    @Override
    protected void onTick(ServerLevel level, int tick) {
        if (tick < AttackKind.LAVA_RAIN.hitTick(0)) {
            this.faceTarget();
        }
    }

    private Vec3 aimPoint() {
        LivingEntity target = this.boss.getTarget();
        return target != null ? target.position() : this.boss.arenaCentreVec();
    }
}
