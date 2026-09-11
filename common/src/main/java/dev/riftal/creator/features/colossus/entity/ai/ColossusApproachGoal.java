package dev.riftal.creator.features.colossus.entity.ai;

import dev.riftal.creator.features.colossus.AttackKind;
import dev.riftal.creator.features.colossus.entity.AshenColossusEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Walks the boss to its target while nothing else is playing. Repaths every half second instead of
 * every tick - a 600 HP boss chasing one player does not need 20 path searches a second.
 */
public class ColossusApproachGoal extends Goal {

    private static final double STOP_DISTANCE_SQ = 9.0D;
    private static final int REPATH_INTERVAL_TICKS = 10;

    private final AshenColossusEntity boss;
    private final double speedModifier;
    private int repathCooldown;

    public ColossusApproachGoal(AshenColossusEntity boss, double speedModifier) {
        this.boss = boss;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.boss.isDeadOrDying() || this.boss.getAttack() != AttackKind.NONE) {
            return false;
        }
        LivingEntity target = this.boss.getTarget();
        return target != null && target.isAlive() && this.boss.distanceToSqr(target) > STOP_DISTANCE_SQ;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
    }

    @Override
    public void stop() {
        this.boss.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.boss.getTarget();
        if (target == null) {
            return;
        }
        this.boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (--this.repathCooldown <= 0) {
            this.repathCooldown = REPATH_INTERVAL_TICKS;
            this.boss.getNavigation().moveTo(target, this.speedModifier);
        }
    }
}
