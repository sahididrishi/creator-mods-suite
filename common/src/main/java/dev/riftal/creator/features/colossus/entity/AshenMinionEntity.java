package dev.riftal.creator.features.colossus.entity;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.colossus.ColossusFeature;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The small ash crawler the Colossus calls up in phase 2 and 3.
 *
 * <p>Minions are bound to the boss that summoned them: they are never hit by its shockwave or its
 * ring of fire, and they crumble to smoke the moment that boss is gone, so the arena is clean for
 * the next take.
 */
public class AshenMinionEntity extends Monster implements GeoEntity {

    /** How often (in ticks) a minion checks that its boss is still alive. */
    public static final int BOSS_CHECK_INTERVAL_TICKS = 20;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.minion.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.minion.walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.minion.attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private UUID bossId;

    public AshenMinionEntity(EntityType<? extends AshenMinionEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 2.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1,
                new HurtByTargetGoal(this, AshenColossusEntity.class, AshenMinionEntity.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Ties this minion's life to {@code boss}. */
    public void bindToBoss(AshenColossusEntity boss) {
        this.bossId = boss.getUUID();
    }

    public boolean isBoundTo(AshenColossusEntity boss) {
        return this.bossId != null && this.bossId.equals(boss.getUUID());
    }

    @Nullable
    public UUID bossId() {
        return this.bossId;
    }

    /** Crumbles the minion away. Used when the boss dies. */
    public void dismiss() {
        if (this.level() instanceof ServerLevel level) {
            Fx.particles(level, ParticleTypes.LARGE_SMOKE, this.position().add(0.0D, 0.5D, 0.0D),
                    16, 0.4D, 0.03D);
            Fx.particles(level, ParticleTypes.ASH, this.position().add(0.0D, 0.5D, 0.0D),
                    12, 0.5D, 0.02D);
        }
        this.discard();
    }

    @Override
    public void tick() {
        super.tick();
        // The grace period stops a minion dismissing itself at world load, when it can easily
        // tick before the boss entity has been indexed in the level.
        if (this.bossId == null || this.tickCount < 40 || this.tickCount % BOSS_CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        if (this.level() instanceof ServerLevel level) {
            Entity boss = level.getEntity(this.bossId);
            if (!(boss instanceof AshenColossusEntity colossus) || !colossus.isAlive()) {
                this.dismiss();
            }
        }
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        boolean hit = super.doHurtTarget(entity);
        if (hit && !this.level().isClientSide) {
            this.triggerAnim("attack", "strike");
        }
        return hit;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ColossusFeature.minionHurtSound();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ColossusFeature.minionDeathSound();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return this.bossId == null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.bossId != null) {
            compound.putUUID("BossUuid", this.bossId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.bossId = compound.hasUUID("BossUuid") ? compound.getUUID("BossUuid") : null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 4,
                state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
        controllers.add(new AnimationController<AshenMinionEntity>(this, "attack", 0,
                state -> PlayState.STOP).triggerableAnim("strike", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
