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
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Apex beast: the shape a stage-5 player wears.
 *
 * <p>In normal play the <em>server</em> never spawns one - the client keeps a render proxy per Apex
 * player ({@code BeastProxyManager}) and draws that instead of the player's body. It is still a
 * complete entity so that {@code /summon creator_evolve:apex_beast} works for b-roll and so the
 * GameTests can prove its attributes and renderer are wired. Its {@code MobCategory} is
 * {@code MISC}, so it never spawns naturally, and it has no targeting goal - it wanders, looks
 * around, and only fights back when hit.
 *
 * <p>Animation is GeckoLib, per the plan: {@code assets/creator_evolve/geo/entity/apex_beast.geo.json}
 * and {@code animations/entity/apex_beast.animation.json}, driven by the two controllers below.
 * The same controllers run on a render proxy, which is why {@link #setRoaringUntil} exists - a
 * proxy is never ticked and has no target, so the client tells it when to roar.
 */
public class ApexBeast extends PathfinderMob implements GeoEntity {

    /**
     * Height of the authored mesh, in blocks, and therefore the entity type's hitbox height.
     *
     * <p>The geo is built to exactly 74.88 px: feet at 0, crown of the horns at 74.88, 74.88 / 16 =
     * 4.68. That number is the plan's stage-5 player hitbox (0.6 x 1.8 scaled by 2.6 gives 1.56 x
     * 4.68), so at Apex the beast a player wears is drawn at world scale 1 and stands exactly as
     * tall as the hitbox it replaces. {@code PlayerRenderSwap} divides by this, so if the mesh ever
     * changes height this constant has to move with it or the beast's head leaves its own box.
     */
    public static final float MODEL_HEIGHT = 4.68F;

    /** Visual width of the authored model, in blocks. */
    public static final float MODEL_WIDTH = 1.56F;

    /** How long one triggered roar lasts, in ticks. Matches {@code animation.apex.roar}. */
    public static final int ROAR_TICKS = 30;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.apex.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.apex.walk");
    private static final RawAnimation ROAR = RawAnimation.begin().thenLoop("animation.apex.roar");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.apex.attack");

    /** Game time at which a triggered roar stops. Client-side on a proxy; unused on the server. */
    private long roarUntilGameTime;

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

    /**
     * Starts a roar that runs until {@code gameTime}. Called by the client's proxy manager when
     * {@code TransformFxPayload.ROAR} arrives - a proxy is never ticked and never has a target, so
     * without this it could never play the animation the plan's Apex beat is built around.
     */
    public void setRoaringUntil(long gameTime) {
        this.roarUntilGameTime = Math.max(this.roarUntilGameTime, gameTime);
    }

    /** True while the roar pose should be playing. */
    public boolean isRoaring() {
        if (this.level().getGameTime() < this.roarUntilGameTime) {
            return true;
        }
        LivingEntity target = this.getTarget();
        return target != null && this.distanceToSqr(target) < 36.0D;
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 5, state -> {
            if (this.isRoaring()) {
                return state.setAndContinue(ROAR);
            }
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));

        // swinging is set from ClientboundAnimatePacket for a real beast, and copied straight off
        // the player for a proxy, so one controller covers both. It sits on its own controller so
        // a swing can overlay the walk cycle instead of replacing it.
        controllers.add(new AnimationController<>(this, "swing", 0, state -> {
            if (this.swinging) {
                return state.setAndContinue(ATTACK);
            }
            return PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
