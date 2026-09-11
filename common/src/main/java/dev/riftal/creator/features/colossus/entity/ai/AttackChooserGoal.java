package dev.riftal.creator.features.colossus.entity.ai;

import dev.riftal.creator.features.colossus.AttackKind;
import dev.riftal.creator.features.colossus.AttackSelector;
import dev.riftal.creator.features.colossus.entity.AshenColossusEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * The brain. Runs only while the boss is idle and off cooldown, asks {@link AttackSelector} what to
 * do next, and writes the answer into the boss' synched attack slot - which is what wakes the
 * matching {@link AnimatedAttackGoal} up on the next AI tick.
 *
 * <p>Holds no control flags, so it never fights the attack goals for the boss' feet.
 */
public class AttackChooserGoal extends Goal {

    private final AshenColossusEntity boss;
    private AttackKind chosen = AttackKind.NONE;

    public AttackChooserGoal(AshenColossusEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (this.boss.isDeadOrDying() || this.boss.isStaggered()) {
            return false;
        }
        if (this.boss.getAttack() != AttackKind.NONE || !this.boss.globalCooldownReady()) {
            return false;
        }
        LivingEntity target = this.boss.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        int minions = this.boss.level() instanceof ServerLevel level
                ? this.boss.aliveMinionCount(level)
                : 0;
        this.chosen = AttackSelector.choose(this.boss.phase(), this.boss.targetDistanceSq(),
                this.boss.cooldowns(), minions, this.boss.attackHistory(), this.boss.getRandom());
        return this.chosen != AttackKind.NONE;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        this.boss.setAttack(this.chosen);
        this.chosen = AttackKind.NONE;
    }
}
