package dev.riftal.creator.features.colossus.entity.ai;

import dev.riftal.creator.features.colossus.AttackKind;
import dev.riftal.creator.features.colossus.entity.AshenColossusEntity;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Base class for every scripted Colossus attack: one goal per {@link AttackKind}.
 *
 * <p>The goal owns the boss for exactly {@link AttackKind#durationTicks()} ticks and calls
 * {@link #onHit} on each of the kind's hit ticks. That timeline lives on the server; GeckoLib's
 * animation clock is render-side only and is never consulted here, which is what keeps damage
 * lined up with the visuals on a laggy client.
 *
 * <p>Holding {@code MOVE}, {@code LOOK} and {@code JUMP} is what stops the approach goal while an
 * attack is playing - the boss plants its feet to swing.
 */
public abstract class AnimatedAttackGoal extends Goal {

    protected final AshenColossusEntity boss;
    protected final AttackKind kind;
    protected int attackTicks;
    private boolean interrupted;

    protected AnimatedAttackGoal(AshenColossusEntity boss, AttackKind kind) {
        this.boss = boss;
        this.kind = kind;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return !this.boss.isDeadOrDying() && this.boss.getAttack() == this.kind;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.boss.isDeadOrDying()
                && this.boss.getAttack() == this.kind
                && this.attackTicks < this.kind.durationTicks();
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.attackTicks = 0;
        this.interrupted = false;
        this.boss.getNavigation().stop();
        this.boss.triggerAnim("attack", this.kind.animName());
        ServerLevel level = this.serverLevel();
        if (level != null) {
            this.onStart(level);
        }
    }

    @Override
    public void tick() {
        ServerLevel level = this.serverLevel();
        if (level == null) {
            return;
        }
        this.attackTicks++;
        for (int i = 0; i < this.kind.hitCount(); i++) {
            if (this.kind.hitTick(i) == this.attackTicks) {
                this.onHit(level, i);
            }
        }
        this.onTick(level, this.attackTicks);
    }

    @Override
    public void stop() {
        this.interrupted = this.attackTicks < this.kind.durationTicks();
        if (this.boss.getAttack() == this.kind) {
            this.boss.setAttack(AttackKind.NONE);
        }
        this.boss.startCooldown(this.kind);
        ServerLevel level = this.serverLevel();
        if (level != null) {
            this.onStop(level);
        }
        this.attackTicks = 0;
    }

    /** Ticks elapsed since the clip started. */
    public int attackTicks() {
        return this.attackTicks;
    }

    /**
     * True inside {@link #onStop} when the clip was cut short - a stagger, a phase roar, death or
     * the boss losing its footing - rather than running to the end of its timeline. Anything a goal
     * left running past its own last tick (a scheduled volley, say) must be torn down when this is
     * true and left alone when it is false.
     */
    protected boolean wasInterrupted() {
        return this.interrupted;
    }

    /** Keeps the boss facing its target - call from {@link #onTick} before the hit tick. */
    protected void faceTarget() {
        LivingEntity target = this.boss.getTarget();
        if (target == null) {
            return;
        }
        this.boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
        this.boss.lookAt(EntityAnchorArgument.Anchor.FEET, target.position());
        // The body has to come round too, or a planted boss swings past the player.
        this.boss.setYBodyRot(this.boss.getYRot());
        this.boss.yHeadRot = this.boss.getYRot();
    }

    @Nullable
    protected ServerLevel serverLevel() {
        return this.boss.level() instanceof ServerLevel level ? level : null;
    }

    /** Called once, on the tick the clip starts. */
    protected void onStart(ServerLevel level) {
    }

    /** Called on each of the kind's hit ticks, {@code index} counting from 0. */
    protected void onHit(ServerLevel level, int index) {
    }

    /** Called every tick of the clip, {@code tick} counting from 1. */
    protected void onTick(ServerLevel level, int tick) {
    }

    /** Called once, when the clip ends or is interrupted. */
    protected void onStop(ServerLevel level) {
    }
}
