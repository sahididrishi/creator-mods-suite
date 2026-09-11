package dev.riftal.creator.features.evolve.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Apex beast: the shape a stage-5 player wears.
 *
 * <p>In normal play it is never spawned - the client draws its model in place of the player, with
 * no entity involved. It is still a complete entity so that {@code /summon
 * creator_evolve:apex_beast} works for b-roll and so the GameTests can prove its attributes and
 * renderer are wired. Its {@code MobCategory} is {@code MISC}, so it never spawns naturally, and it
 * has no targeting goal - it wanders, looks around, and only fights back when hit.
 */
public class ApexBeast extends PathfinderMob {

    /** Visual height of the authored model, in blocks. Matches the entity type's hitbox height. */
    public static final float MODEL_HEIGHT = 4.68F;

    /** Visual width of the authored model, in blocks. */
    public static final float MODEL_WIDTH = 1.56F;

    public ApexBeast(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 30;
    }

    /** Registered through {@code EntityAttributes.register} in {@code EvolveFeature}. */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.STEP_HEIGHT, 1.5D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** True while the beast has a target in reach - the renderer plays the roar pose. */
    public boolean isRoaring() {
        LivingEntity target = this.getTarget();
        return target != null && this.distanceToSqr(target) < 36.0D;
    }
}
