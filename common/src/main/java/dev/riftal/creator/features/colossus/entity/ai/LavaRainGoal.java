package dev.riftal.creator.features.colossus.entity.ai;

import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.features.colossus.AttackKind;
import dev.riftal.creator.features.colossus.ColossusFeature;
import dev.riftal.creator.features.colossus.entity.AshenColossusEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Phase 2+: the Colossus throws both arms up and a volley of ash bombs arcs down around whoever it
 * is fighting. The volley is spread over {@value #VOLLEY_SPACING_TICKS}-tick intervals with the
 * shared {@link TickScheduler} rather than a per-tick loop, and re-aims at the live target on each
 * bomb so a player who runs still gets rained on.
 *
 * <p>The volley deliberately outlives the animation by a few ticks - the last bombs are still in
 * the air as the boss lowers its arms - but it must <em>not</em> outlive an interruption. A stagger
 * or a phase roar landing mid-cast tears the remaining bombs down in {@link #onStop}, or the boss
 * would keep lobbing slag out of a body that is visibly reeling.
 */
public class LavaRainGoal extends AnimatedAttackGoal {

    /** Ticks between bombs in a volley. */
    public static final int VOLLEY_SPACING_TICKS = 2;

    public LavaRainGoal(AshenColossusEntity boss) {
        super(boss, AttackKind.LAVA_RAIN);
    }

    /**
     * Scheduler tag for one boss' volley. Per boss, not per feature: two Colossi fighting at once
     * is an explicit scenario in the plan, and a shared tag would have either one's stagger cancel
     * the other's rain.
     */
    public static ResourceLocation volleyTag(AshenColossusEntity boss) {
        return ResourceLocation.fromNamespaceAndPath(ColossusFeature.NAMESPACE,
                "lava_rain/" + boss.getUUID().toString().toLowerCase(Locale.ROOT));
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
        }).tag(volleyTag(this.boss));
    }

    @Override
    protected void onTick(ServerLevel level, int tick) {
        if (tick < AttackKind.LAVA_RAIN.hitTick(0)) {
            this.faceTarget();
        }
    }

    @Override
    protected void onStop(ServerLevel level) {
        // Only on an interrupt: a volley that reached the end of the clip is meant to finish
        // falling, and cancelling it here would silently drop the last two bombs of every cast.
        if (this.wasInterrupted()) {
            TickScheduler.cancelAll(volleyTag(this.boss));
        }
    }

    private Vec3 aimPoint() {
        LivingEntity target = this.boss.getTarget();
        return target != null ? target.position() : this.boss.arenaCentreVec();
    }
}
