package dev.riftal.creator.features.vault.entity;

import dev.riftal.creator.features.vault.VaultFeature;
import dev.riftal.creator.features.vault.block.entity.CursedAltarBlockEntity;
import dev.riftal.creator.features.vault.entity.ai.KeeperGuardAltarGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
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

import org.jetbrains.annotations.Nullable;

/**
 * The Vault Keeper: a persistent mini-boss bound to the Cursed Altar that summoned it.
 *
 * <p>It never spawns naturally - nothing in any biome lists it - so the only ways it enters a world
 * are the altar's charge countdown, {@code /vault spawn_keeper} and a spawn egg. It will not
 * despawn and it will not wander far from its altar, which is what makes the fight repeatable
 * take after take.
 */
public class VaultKeeper extends Monster {

    /** How far the Keeper may stray from its altar before it walks back. */
    public static final double LEASH_RADIUS = 20.0D;

    /** Beyond this it is yanked back, so it cannot be kited out of the treasure room. */
    public static final double TETHER_RADIUS = 48.0D;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    @Nullable
    private BlockPos altarPos;

    public VaultKeeper(EntityType<? extends VaultKeeper> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 40;
    }

    /** Attribute defaults. Registered through {@code EntityAttributes.register} in the feature. */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(2, new KeeperGuardAltarGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ------------------------------------------------------------- altar bond

    /**
     * Binds this Keeper to the altar at {@code pos}; its death unseals that altar's chests.
     *
     * @param pos the altar, or {@code null} to cut the Keeper loose - no tether, and its death
     *            reports to nothing
     */
    public void bindToAltar(@Nullable BlockPos pos) {
        this.altarPos = pos == null ? null : pos.immutable();
    }

    /** The altar this Keeper belongs to, or {@code null} for an unbound one. */
    @Nullable
    public BlockPos altarPos() {
        return this.altarPos;
    }

    // ----------------------------------------------------------------- boss bar

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
    protected void customServerAiStep() {
        super.customServerAiStep();
        float max = getMaxHealth();
        this.bossEvent.setProgress(max > 0.0F ? getHealth() / max : 0.0F);
        tetherToAltar();
    }

    /**
     * The hard leash, run every tick whatever the Keeper is doing.
     *
     * <p>Deliberately <em>not</em> inside {@link KeeperGuardAltarGoal}: a goal only ticks while it
     * is the selected goal, and the walk-home goal steps aside as soon as the Keeper has a target -
     * which is exactly the situation a player kiting it up the entrance shaft creates. The soft
     * "walk back" behaviour stays in the goal; the yank does not.
     */
    private void tetherToAltar() {
        if (this.altarPos == null) {
            return;
        }
        double distanceSqr = distanceToSqr(this.altarPos.getX() + 0.5D, this.altarPos.getY() + 0.5D,
                this.altarPos.getZ() + 0.5D);
        if (distanceSqr <= TETHER_RADIUS * TETHER_RADIUS) {
            return;
        }
        getNavigation().stop();
        teleportTo(this.altarPos.getX() + 0.5D, this.altarPos.getY() + 1.0D, this.altarPos.getZ() + 0.5D);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // --------------------------------------------------------------- lifecycle

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        this.bossEvent.setProgress(0.0F);
        this.bossEvent.removeAllPlayers();
        notifyAltar();
    }

    /**
     * Tells the bound altar immediately rather than making it wait for its 20-tick poll, so the
     * chest cracks open on the same tick the Keeper falls over.
     *
     * <p>Reports <em>this</em> Keeper's UUID, and the altar drops the report unless it is the
     * Keeper it is actually waiting for. Without that check any leftover Keeper from an earlier
     * take - one adopted away, one that survived a {@code /vault reset} in an unloaded chunk, one
     * caught by a stray {@code /kill} - would crack the chest open mid-fight.
     */
    private void notifyAltar() {
        if (this.altarPos == null || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!serverLevel.isLoaded(this.altarPos)) {
            return;
        }
        if (serverLevel.getBlockEntity(this.altarPos) instanceof CursedAltarBlockEntity altar) {
            altar.onKeeperDead(serverLevel, getUUID());
        }
    }

    // -------------------------------------------------------------------- data

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.altarPos != null) {
            compound.putInt("AltarX", this.altarPos.getX());
            compound.putInt("AltarY", this.altarPos.getY());
            compound.putInt("AltarZ", this.altarPos.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("AltarX") && compound.contains("AltarY") && compound.contains("AltarZ")) {
            this.altarPos = new BlockPos(compound.getInt("AltarX"), compound.getInt("AltarY"),
                    compound.getInt("AltarZ"));
        } else {
            this.altarPos = null;
        }
    }

    // ------------------------------------------------------------------ sounds

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return VaultFeature.KEEPER_IDLE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return VaultFeature.KEEPER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return VaultFeature.KEEPER_DEATH.get();
    }
}
