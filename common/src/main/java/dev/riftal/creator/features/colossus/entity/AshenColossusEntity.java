package dev.riftal.creator.features.colossus.entity;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.core.util.MathUtil;
import dev.riftal.creator.core.util.Selection;
import dev.riftal.creator.features.colossus.ArenaRing;
import dev.riftal.creator.features.colossus.AttackKind;
import dev.riftal.creator.features.colossus.AttackSelector;
import dev.riftal.creator.features.colossus.BossPhase;
import dev.riftal.creator.features.colossus.ColossusFeature;
import dev.riftal.creator.features.colossus.FirePatches;
import dev.riftal.creator.features.colossus.Shockwave;
import dev.riftal.creator.features.colossus.arena.Arena;
import dev.riftal.creator.features.colossus.entity.ai.AttackChooserGoal;
import dev.riftal.creator.features.colossus.entity.ai.ColossusApproachGoal;
import dev.riftal.creator.features.colossus.entity.ai.ComboGoal;
import dev.riftal.creator.features.colossus.entity.ai.LavaRainGoal;
import dev.riftal.creator.features.colossus.entity.ai.RoarGoal;
import dev.riftal.creator.features.colossus.entity.ai.SlamGoal;
import dev.riftal.creator.features.colossus.entity.ai.SpawnRiseGoal;
import dev.riftal.creator.features.colossus.entity.ai.StaggerGoal;
import dev.riftal.creator.features.colossus.entity.ai.SummonMinionsGoal;
import dev.riftal.creator.features.colossus.net.ScreenShakePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.riftal.creator.Constants.LOG;

/**
 * The Ashen Colossus: a five-block, three-phase boss.
 *
 * <p>The server owns the fight's timeline. Every attack is an {@link AttackKind} held in synched
 * data; one goal per kind runs it, firing damage on the kind's hit ticks. GeckoLib only ever plays
 * back what the server already decided, so damage and animation cannot drift apart on a laggy
 * client.
 *
 * <p>Phases are recomputed from health every tick and never run backwards during a fight. Entering
 * a phase roars (invulnerable for the clip), recolours the boss bar and, in phase 3, starts the
 * closing ring of fire.
 */
public class AshenColossusEntity extends Monster implements GeoEntity {

    /** Total XP dropped by the death burst. */
    public static final int XP_REWARD = 800;

    /** Ticks between the boss dying and the loot landing - the length of the collapse. */
    public static final int DEATH_TICKS = AttackKind.DEATH.durationTicks();

    /** Minimum gap between any two attacks. */
    public static final int GLOBAL_ATTACK_COOLDOWN = 40;

    /** Slam: damage at the leading edge of the ring. */
    public static final float SLAM_DAMAGE = 10.0F;

    /** Slam: how hard victims are thrown. */
    public static final double SLAM_KNOCKBACK = 1.6D;

    /** Slam: how far the ring travels. */
    public static final double SLAM_MAX_RADIUS = 7.0D;

    /** Combo: damage of the three hits, in order. */
    public static final float[] COMBO_DAMAGE = {8.0F, 8.0F, 12.0F};

    /** Combo: reach of each hit. */
    public static final double COMBO_REACH = 3.5D;

    /** Lava rain: how many bombs one cast throws. */
    public static final int LAVA_RAIN_BOMBS = 12;

    /** Lava rain: how far from the target the bombs scatter. */
    public static final double LAVA_RAIN_SPREAD = 6.0D;

    /** How many minions one summon brings. */
    public static final int MINIONS_PER_SUMMON = 3;

    /** Id of the +50 % movement speed modifier added on entering phase 3. */
    public static final ResourceLocation ENRAGE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(ColossusFeature.NAMESPACE, "enrage");

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(AshenColossusEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACK =
            SynchedEntityData.defineId(AshenColossusEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RING_RADIUS =
            SynchedEntityData.defineId(AshenColossusEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<BlockPos> DATA_ARENA_CENTRE =
            SynchedEntityData.defineId(AshenColossusEntity.class, EntityDataSerializers.BLOCK_POS);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.colossus.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.colossus.walk");
    private static final RawAnimation DEATH =
            RawAnimation.begin().thenPlayAndHold("animation.colossus.death");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_6);

    private final Deque<AttackKind> history = new ArrayDeque<>(4);
    private final List<UUID> minions = new ArrayList<>();

    private int globalCooldown;
    private int slamCooldown;
    private int lavaRainCooldown;
    private int summonCooldown;
    private int comboCooldown;

    private int attackAgeTicks;
    private int staggerTicks;
    private int ticksInPhase3;
    private int arenaRadius = Arena.DEFAULT_RADIUS;
    private String arenaName = Arena.DEFAULT_NAME;
    private boolean arenaBound;

    @Nullable
    private DamageSource deferredDeathSource;
    private boolean lootDropped;

    public AshenColossusEntity(EntityType<? extends AshenColossusEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
    }

    /** Registered from {@code ColossusFeature#registerContent()}; a missing entry crashes on spawn. */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600.0D)
                .add(Attributes.ATTACK_DAMAGE, 14.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.STEP_HEIGHT, 1.5D);
    }

    // ------------------------------------------------------------------ synched state

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, BossPhase.P1.index());
        builder.define(DATA_ATTACK, AttackKind.NONE.id());
        builder.define(DATA_RING_RADIUS, (float) Arena.DEFAULT_RADIUS);
        builder.define(DATA_ARENA_CENTRE, BlockPos.ZERO);
    }

    /** 1, 2 or 3. Readable on the client, which uses it to pick the enraged texture. */
    public int getPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    public BossPhase phase() {
        return BossPhase.byIndex(this.getPhase());
    }

    public AttackKind getAttack() {
        return AttackKind.byId(this.entityData.get(DATA_ATTACK));
    }

    /** Starts a scripted action. The matching goal picks it up on the next AI tick. */
    public void setAttack(AttackKind kind) {
        if (this.getAttack() == kind) {
            return;
        }
        this.entityData.set(DATA_ATTACK, kind.id());
        this.attackAgeTicks = 0;
    }

    /** Current radius of the phase 3 fire ring. Synched so a relogging player sees it correctly. */
    public float getRingRadius() {
        return this.entityData.get(DATA_RING_RADIUS);
    }

    /** Centre of the arena this boss is bound to. Synched for the client ring warning. */
    public BlockPos getArenaCentre() {
        return this.entityData.get(DATA_ARENA_CENTRE);
    }

    public Vec3 arenaCentreVec() {
        return Vec3.atBottomCenterOf(this.getArenaCentre());
    }

    public int getArenaRadius() {
        return this.arenaRadius;
    }

    public String getArenaName() {
        return this.arenaName;
    }

    /** Binds the boss to an arena. Called by {@code finalizeSpawn} and by {@code /colossus arena set}. */
    public void bindArena(String name, BlockPos centre, int radius) {
        this.arenaName = name;
        this.arenaRadius = Math.max(Arena.MIN_RADIUS, Math.min(Arena.MAX_RADIUS, radius));
        this.arenaBound = true;
        this.entityData.set(DATA_ARENA_CENTRE, centre);
        // Re-binding mid-fight keeps whatever the ring has already closed to, but never lets it
        // sit outside the new arena.
        float current = this.getRingRadius() <= 0.0F ? this.arenaRadius : this.getRingRadius();
        this.entityData.set(DATA_RING_RADIUS,
                Math.max((float) ArenaRing.MIN_RADIUS, Math.min(current, this.arenaRadius)));
        this.restrictTo(centre, this.arenaRadius);
    }

    public boolean hasArena() {
        return this.arenaBound;
    }

    // ------------------------------------------------------------------ goals

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SpawnRiseGoal(this));
        this.goalSelector.addGoal(1, new RoarGoal(this));
        this.goalSelector.addGoal(1, new StaggerGoal(this));
        this.goalSelector.addGoal(1, new SlamGoal(this));
        this.goalSelector.addGoal(1, new LavaRainGoal(this));
        this.goalSelector.addGoal(1, new SummonMinionsGoal(this));
        this.goalSelector.addGoal(1, new ComboGoal(this));
        this.goalSelector.addGoal(2, new AttackChooserGoal(this));
        this.goalSelector.addGoal(3, new ColossusApproachGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, 0.9D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, AshenMinionEntity.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel serverLevel) {
            this.serverTick(serverLevel);
        }
    }

    /**
     * The fight's heartbeat. Runs from {@link #tick()} rather than {@code customServerAiStep()} so
     * the boss bar, the phase machine and the ring keep working while the AI is frozen for a take.
     */
    private void serverTick(ServerLevel level) {
        if (this.tickCount % 2 == 0) {
            this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        }

        if (this.isDeadOrDying()) {
            return;
        }

        this.globalCooldown = Math.max(0, this.globalCooldown - 1);
        this.slamCooldown = Math.max(0, this.slamCooldown - 1);
        this.lavaRainCooldown = Math.max(0, this.lavaRainCooldown - 1);
        this.summonCooldown = Math.max(0, this.summonCooldown - 1);
        this.comboCooldown = Math.max(0, this.comboCooldown - 1);
        this.staggerTicks = Math.max(0, this.staggerTicks - 1);

        AttackKind attack = this.getAttack();
        if (attack == AttackKind.NONE) {
            this.attackAgeTicks = 0;
        } else if (++this.attackAgeTicks > attack.durationTicks() + 60) {
            // The owning goal never ran (frozen AI, chunk edge, a mod disabling goals).
            // Never leave the boss stuck mid-attack - especially not mid-roar, which is invulnerable.
            this.setAttack(AttackKind.NONE);
        }

        BossPhase wanted = BossPhase.next(this.phase(), this.getHealth() / this.getMaxHealth());
        if (wanted.index() > this.getPhase()) {
            this.enterPhase(level, wanted);
        }

        if (this.phase() == BossPhase.P3) {
            this.tickArenaRing(level);
        }

        if (this.tickCount % 20 == 0) {
            this.pruneMinions(level);
        }
    }

    private void tickArenaRing(ServerLevel level) {
        this.ticksInPhase3++;
        double radius = ArenaRing.radiusAt(this.arenaRadius, ArenaRing.MIN_RADIUS,
                ArenaRing.BLOCKS_PER_SECOND, this.ticksInPhase3);
        this.entityData.set(DATA_RING_RADIUS, (float) radius);

        Vec3 centre = this.arenaCentreVec();
        if (this.ticksInPhase3 % ArenaRing.DRAW_INTERVAL_TICKS == 0) {
            ArenaRing.draw(level, centre, radius);
        }
        if (this.ticksInPhase3 % ArenaRing.BURN_INTERVAL_TICKS == 0) {
            ArenaRing.burnOutsiders(level, this, centre, radius, this.arenaRadius + 24.0D,
                    victim -> !this.isOwnMinion(victim));
        }
    }

    /**
     * Forces the boss into a phase and replays the roar, in either direction. This is what
     * {@code /colossus phase} calls, so a creator can re-shoot the phase 3 entrance without
     * killing and re-spawning the boss.
     */
    public void forcePhase(BossPhase phase) {
        if (this.level() instanceof ServerLevel level) {
            this.enterPhase(level, phase);
        }
    }

    /** Phase change: roar, recolour the bar, and in phase 3 enrage and start the ring. */
    private void enterPhase(ServerLevel level, BossPhase phase) {
        this.entityData.set(DATA_PHASE, phase.index());
        this.applyBarStyle(phase);

        this.interruptAttack();
        this.setAttack(AttackKind.ROAR);

        this.ticksInPhase3 = 0;
        this.entityData.set(DATA_RING_RADIUS, (float) this.arenaRadius);
        if (phase == BossPhase.P3) {
            this.applyEnrage();
        } else {
            this.removeEnrage();
        }

        ScreenShakePayload.sendAround(level, this.position(), 0.4F, 20, 28.0F);
        Fx.sound(level, this.position(), ColossusFeature.roarSound(), SoundSource.HOSTILE, 3.0F, 0.8F);
        LOG.info("[colossus] phase {} at {}/{} hp", phase.index(),
                (int) this.getHealth(), (int) this.getMaxHealth());
    }

    private void applyBarStyle(BossPhase phase) {
        this.bossEvent.setColor(phase.barColor());
        this.bossEvent.setDarkenScreen(phase == BossPhase.P3);
    }

    private void applyEnrage() {
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || speed.hasModifier(ENRAGE_MODIFIER_ID)) {
            return;
        }
        speed.addPermanentModifier(new AttributeModifier(ENRAGE_MODIFIER_ID, 0.5D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private void removeEnrage() {
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.hasModifier(ENRAGE_MODIFIER_ID)) {
            speed.removeModifier(ENRAGE_MODIFIER_ID);
        }
    }

    // ------------------------------------------------------------------ attacks

    /** Squared distance to the current target, or {@link Double#MAX_VALUE} when there is none. */
    public double targetDistanceSq() {
        LivingEntity target = this.getTarget();
        return target == null ? Double.MAX_VALUE : this.distanceToSqr(target);
    }

    public AttackSelector.Cooldowns cooldowns() {
        return new AttackSelector.Cooldowns(this.slamCooldown, this.lavaRainCooldown,
                this.summonCooldown, this.comboCooldown);
    }

    public boolean globalCooldownReady() {
        return this.globalCooldown <= 0;
    }

    public Deque<AttackKind> attackHistory() {
        return this.history;
    }

    /** Records an attack and starts both its own cooldown and the global one. */
    public void startCooldown(AttackKind kind) {
        this.globalCooldown = GLOBAL_ATTACK_COOLDOWN;
        switch (kind) {
            case SLAM -> this.slamCooldown = kind.cooldownTicks();
            case LAVA_RAIN -> this.lavaRainCooldown = kind.cooldownTicks();
            case SUMMON -> this.summonCooldown = kind.cooldownTicks();
            case COMBO -> this.comboCooldown = kind.cooldownTicks();
            default -> {
            }
        }
        if (kind.isChoosable()) {
            this.history.addLast(kind);
            while (this.history.size() > 4) {
                this.history.removeFirst();
            }
        }
    }

    /** Stops whatever is playing and clears the attack slot. */
    public void interruptAttack() {
        this.stopTriggeredAnim("attack", null);
        this.setAttack(AttackKind.NONE);
    }

    /** {@code /colossus stagger}: interrupt, play the stagger clip, take double damage for its length. */
    public void stagger() {
        this.interruptAttack();
        this.staggerTicks = AttackKind.STAGGER.durationTicks();
        this.setAttack(AttackKind.STAGGER);
    }

    public boolean isStaggered() {
        return this.staggerTicks > 0;
    }

    /** Drives the slam's expanding ring. Called once per tick by {@code SlamGoal} after the hit tick. */
    public void emitSlamRing(ServerLevel level, int tickSinceImpact, Set<UUID> alreadyHit) {
        double radius = Shockwave.radiusAt(tickSinceImpact, Shockwave.EXPANSION_TICKS, SLAM_MAX_RADIUS);
        float damage = SLAM_DAMAGE + (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.25F;
        Shockwave.apply(level, this, this.position(), radius, damage, SLAM_KNOCKBACK, alreadyHit,
                victim -> !this.isOwnMinion(victim));
    }

    /** Throws one ash bomb at a scattered point around {@code aim}. */
    public void throwAshBomb(ServerLevel level, Vec3 aim) {
        AshBombEntity bomb = ColossusFeature.ashBomb().get().create(level);
        if (bomb == null) {
            return;
        }
        double angle = this.random.nextDouble() * Math.PI * 2.0D;
        double distance = this.random.nextDouble() * LAVA_RAIN_SPREAD;
        Vec3 landing = aim.add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
        Vec3 launch = this.position().add(0.0D, this.getBbHeight() * 0.85D, 0.0D);

        bomb.setOwner(this);
        bomb.setPos(launch.x, launch.y, launch.z);
        Vec3 delta = landing.subtract(launch);
        bomb.shoot(delta.x, delta.y * 0.35D + 0.9D, delta.z, 0.55F, 6.0F);
        level.addFreshEntity(bomb);
        Fx.sound(level, launch, ColossusFeature.swingSound(), SoundSource.HOSTILE, 1.6F, 1.2F);
    }

    /** Claws {@link #MINIONS_PER_SUMMON} minions out of the floor around the boss. */
    public void summonMinions(ServerLevel level) {
        int room = AttackSelector.MINION_CAP - this.aliveMinionCount(level);
        int toSpawn = Math.min(MINIONS_PER_SUMMON, room);
        for (int i = 0; i < toSpawn; i++) {
            double angle = (Math.PI * 2.0D * i) / Math.max(1, toSpawn) + this.random.nextDouble();
            Vec3 spot = this.position().add(Math.cos(angle) * 4.0D, 0.0D, Math.sin(angle) * 4.0D);
            AshenMinionEntity minion = ColossusFeature.minion().get().create(level);
            if (minion == null) {
                continue;
            }
            minion.moveTo(spot.x, this.getY(), spot.z, this.random.nextFloat() * 360.0F, 0.0F);
            minion.finalizeSpawn(level, level.getCurrentDifficultyAt(minion.blockPosition()),
                    MobSpawnType.MOB_SUMMONED, null);
            minion.bindToBoss(this);
            minion.setTarget(this.getTarget());
            level.addFreshEntity(minion);
            this.minions.add(minion.getUUID());

            BlockState floor = level.getBlockState(minion.blockPosition().below());
            Fx.particles(level, new BlockParticleOption(ParticleTypes.BLOCK, floor), spot, 24, 0.4D, 0.08D);
            Fx.particles(level, ParticleTypes.LARGE_SMOKE, spot, 10, 0.4D, 0.02D);
        }
    }

    /** Melee sweep in front of the boss - one hit of the phase 3 combo. */
    public void comboHit(ServerLevel level, int index) {
        float damage = COMBO_DAMAGE[Math.min(index, COMBO_DAMAGE.length - 1)];
        Vec3 origin = this.position().add(MathUtil.horizontalLook(this).scale(COMBO_REACH * 0.5D));
        for (LivingEntity victim : Selection.livingAround(level, origin, COMBO_REACH, this)) {
            if (this.isOwnMinion(victim) || !this.isInFrontArc(victim)) {
                continue;
            }
            victim.hurt(level.damageSources().mobAttack(this), damage);
            victim.knockback(0.6D, this.getX() - victim.getX(), this.getZ() - victim.getZ());
        }
        Fx.sound(level, origin, ColossusFeature.swingSound(), SoundSource.HOSTILE, 2.0F, 0.9F);
        Fx.particles(level, ParticleTypes.LARGE_SMOKE, origin.add(0.0D, 1.0D, 0.0D), 12, 0.6D, 0.02D);
    }

    private boolean isInFrontArc(Entity other) {
        Vec3 toOther = other.position().subtract(this.position());
        if (toOther.horizontalDistanceSqr() < 1.0E-4D) {
            return true;
        }
        Vec3 facing = MathUtil.horizontalLook(this);
        Vec3 flat = new Vec3(toOther.x, 0.0D, toOther.z).normalize();
        return facing.dot(flat) > 0.0D;
    }

    // ------------------------------------------------------------------ minions

    public boolean isOwnMinion(Entity entity) {
        return entity instanceof AshenMinionEntity minion && minion.isBoundTo(this);
    }

    /** Live minions this boss owns, pruning ids that no longer resolve. */
    public int aliveMinionCount(ServerLevel level) {
        this.pruneMinions(level);
        return this.minions.size();
    }

    private void pruneMinions(ServerLevel level) {
        Iterator<UUID> it = this.minions.iterator();
        while (it.hasNext()) {
            Entity entity = level.getEntity(it.next());
            if (!(entity instanceof AshenMinionEntity minion) || !minion.isAlive()) {
                it.remove();
            }
        }
    }

    /** Sends every surviving minion away in a puff of smoke. */
    public void dismissMinions(ServerLevel level) {
        for (UUID id : new ArrayList<>(this.minions)) {
            if (level.getEntity(id) instanceof AshenMinionEntity minion) {
                minion.dismiss();
            }
        }
        this.minions.clear();
    }

    // ------------------------------------------------------------------ damage, boss bar, death

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (super.isInvulnerableTo(source)) {
            return true;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return this.getAttack().isInvulnerable();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        float scaled = this.isStaggered() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                ? amount * 2.0F
                : amount;
        return super.hurt(source, scaled);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        this.bossEvent.removeAllPlayers();
        super.remove(reason);
    }

    /** The live boss bar. Exposed for {@code /colossus status} and the GameTests. */
    public ServerBossEvent bossEvent() {
        return this.bossEvent;
    }

    /** {@code /colossus kill}: bypasses the roar invulnerability and starts the collapse. */
    public void startDeath() {
        this.hurt(this.damageSources().genericKill(), Float.MAX_VALUE);
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource damageSource) {
        // Held back until the end of the 70-tick collapse so the loot bursts out of the corpse
        // instead of out of a boss that is still standing. Dropped from tickDeath below.
        this.deferredDeathSource = damageSource;
    }

    @Override
    protected int getBaseExperienceReward() {
        return 0;
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        if (this.deathTime == 1) {
            this.setAttack(AttackKind.DEATH);
            this.dismissMinions(level);
            FirePatches.cancelPending();
            Fx.sound(level, this.position(), ColossusFeature.deathSound(), SoundSource.HOSTILE, 4.0F, 0.9F);
            ScreenShakePayload.sendAround(level, this.position(), 0.5F, 40, 32.0F);
        }

        if (this.deathTime % 6 == 0) {
            Fx.particles(level, ParticleTypes.LARGE_SMOKE,
                    this.position().add(0.0D, this.getBbHeight() * 0.4D, 0.0D), 12, 1.2D, 0.02D);
        }

        if (this.deathTime >= DEATH_TICKS) {
            if (!this.lootDropped) {
                this.lootDropped = true;
                DamageSource source = this.deferredDeathSource != null
                        ? this.deferredDeathSource
                        : this.damageSources().genericKill();
                super.dropAllDeathLoot(level, source);
                ExperienceOrb.award(level, this.position(), XP_REWARD);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                        this.getX(), this.getY() + 1.0D, this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    // ------------------------------------------------------------------ spawning, sounds, saving

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        this.setPersistenceRequired();
        if (!this.arenaBound) {
            this.bindArena(Arena.DEFAULT_NAME, this.blockPosition(), Arena.DEFAULT_RADIUS);
        }
        this.setHealth(this.getMaxHealth());
        this.setAttack(AttackKind.SPAWN);
        return result;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ColossusFeature.hurtSound();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ColossusFeature.deathSound();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(ColossusFeature.stepSound(), 1.5F, 0.7F);
        if (this.level() instanceof ServerLevel level && this.tickCount % 4 == 0) {
            ScreenShakePayload.sendAround(level, this.position(), 0.15F, 4, 12.0F);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Phase", this.getPhase());
        compound.putInt("ArenaRadius", this.arenaRadius);
        compound.putString("ArenaName", this.arenaName);
        compound.putBoolean("ArenaBound", this.arenaBound);
        BlockPos centre = this.getArenaCentre();
        compound.putIntArray("ArenaCentre", new int[]{centre.getX(), centre.getY(), centre.getZ()});
        compound.putFloat("RingRadius", this.getRingRadius());
        compound.putInt("TicksInPhase3", this.ticksInPhase3);
        compound.putInt("SlamCooldown", this.slamCooldown);
        compound.putInt("LavaRainCooldown", this.lavaRainCooldown);
        compound.putInt("SummonCooldown", this.summonCooldown);
        compound.putInt("ComboCooldown", this.comboCooldown);
        compound.putInt("StaggerTicks", this.staggerTicks);

        ListTag ids = new ListTag();
        for (UUID id : this.minions) {
            ids.add(NbtUtils.createUUID(id));
        }
        compound.put("Minions", ids);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.arenaRadius = compound.contains("ArenaRadius")
                ? Math.max(Arena.MIN_RADIUS, Math.min(Arena.MAX_RADIUS, compound.getInt("ArenaRadius")))
                : Arena.DEFAULT_RADIUS;
        this.arenaName = compound.contains("ArenaName") ? compound.getString("ArenaName") : Arena.DEFAULT_NAME;
        this.arenaBound = compound.getBoolean("ArenaBound");
        int[] centre = compound.getIntArray("ArenaCentre");
        BlockPos arenaCentre = centre.length == 3
                ? new BlockPos(centre[0], centre[1], centre[2])
                : this.blockPosition();
        this.entityData.set(DATA_ARENA_CENTRE, arenaCentre);
        if (this.arenaBound) {
            this.restrictTo(arenaCentre, this.arenaRadius);
        }
        this.entityData.set(DATA_RING_RADIUS, compound.contains("RingRadius")
                ? compound.getFloat("RingRadius")
                : (float) this.arenaRadius);
        this.ticksInPhase3 = compound.getInt("TicksInPhase3");
        this.slamCooldown = compound.getInt("SlamCooldown");
        this.lavaRainCooldown = compound.getInt("LavaRainCooldown");
        this.summonCooldown = compound.getInt("SummonCooldown");
        this.comboCooldown = compound.getInt("ComboCooldown");
        this.staggerTicks = compound.getInt("StaggerTicks");

        this.minions.clear();
        ListTag ids = compound.getList("Minions", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < ids.size(); i++) {
            this.minions.add(NbtUtils.loadUUID(ids.get(i)));
        }

        // Phase is authoritative from health, not from disk: a boss healed while unloaded should
        // not still be purple. The stored value only decides whether a roar is owed.
        BossPhase fromHealth = BossPhase.forHealthFraction(this.getHealth() / this.getMaxHealth());
        this.entityData.set(DATA_PHASE, fromHealth.index());
        this.applyBarStyle(fromHealth);
        if (fromHealth == BossPhase.P3) {
            this.applyEnrage();
        }
        this.setAttack(AttackKind.NONE);
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 5, state -> {
            if (this.isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            if (this.getAttack() != AttackKind.NONE) {
                // The attack controller owns the model while a scripted clip is playing.
                return PlayState.STOP;
            }
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }).setAnimationSpeedHandler(boss -> boss.getPhase() >= BossPhase.P3.index() ? 1.5D : 1.0D));

        AnimationController<AshenColossusEntity> attack =
                new AnimationController<>(this, "attack", 0, state -> PlayState.STOP);
        for (AttackKind kind : AttackKind.values()) {
            if (kind == AttackKind.NONE || kind == AttackKind.DEATH) {
                continue;
            }
            attack.triggerableAnim(kind.animName(),
                    RawAnimation.begin().thenPlay("animation.colossus." + kind.animName()));
        }
        controllers.add(attack);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
