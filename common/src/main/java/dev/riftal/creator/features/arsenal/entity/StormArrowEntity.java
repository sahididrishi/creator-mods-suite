package dev.riftal.creator.features.arsenal.entity;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.arsenal.ArsenalFeature;
import dev.riftal.creator.features.arsenal.mechanic.DamageMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Storm Bow's arrow.
 *
 * <p>Behaves exactly like a vanilla arrow unless it was loosed at full draw, in which case it calls
 * a <em>visual-only</em> lightning bolt where it lands and applies its own blast. Visual-only is
 * deliberate: it suppresses vanilla's five points of damage, its fire, its block ignition and its
 * creeper charging, so a creator's build never burns down on camera. The blast damage is ours, and
 * any fire the victim was already carrying is put out.
 */
public class StormArrowEntity extends AbstractArrow {

    private static final EntityDataAccessor<Boolean> DATA_CHARGED =
            SynchedEntityData.defineId(StormArrowEntity.class, EntityDataSerializers.BOOLEAN);

    private boolean struck;

    /** Registry factory constructor. */
    public StormArrowEntity(EntityType<? extends StormArrowEntity> type, Level level) {
        super(type, level);
    }

    /** Fired from a bow. {@code weapon} is the bow stack and must not be empty. */
    public StormArrowEntity(Level level, LivingEntity owner, ItemStack pickup, ItemStack weapon) {
        super(ArsenalFeature.STORM_ARROW.get(), owner, level, pickup, weapon);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHARGED, false);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    /** True when the arrow was loosed at full draw and will call lightning. */
    public boolean isCharged() {
        return this.entityData.get(DATA_CHARGED);
    }

    /** Set at spawn time from the bow's full-draw flag. */
    public void setCharged(boolean charged) {
        this.entityData.set(DATA_CHARGED, charged);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        this.maybeStrike(result.getEntity().position());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.maybeStrike(result.getLocation());
    }

    private void maybeStrike(Vec3 where) {
        if (this.struck || !this.isCharged() || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        this.struck = true;
        this.strike(serverLevel, where);
        this.discard();
    }

    private void strike(ServerLevel level, Vec3 where) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(BlockPos.containing(where)));
            bolt.setVisualOnly(true);
            bolt.setCause(this.getOwner() instanceof ServerPlayer player ? player : null);
            level.addFreshEntity(bolt);
        }

        DamageSource source = level.damageSources().lightningBolt();
        double radiusSq = DamageMath.LIGHTNING_RADIUS * DamageMath.LIGHTNING_RADIUS;
        Entity owner = this.getOwner();
        double size = DamageMath.LIGHTNING_RADIUS * 2.0D;
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(where, size, size, size));
        for (LivingEntity victim : victims) {
            if (victim == owner || !victim.isAlive()) {
                continue;
            }
            double distanceSq = victim.position().distanceToSqr(where);
            if (distanceSq > radiusSq) {
                continue;
            }
            float damage = DamageMath.lightningAoe(Math.sqrt(distanceSq));
            if (damage <= 0.0F) {
                continue;
            }
            victim.hurt(source, damage);
            victim.setRemainingFireTicks(0);
            Vec3 away = victim.position().subtract(where);
            if (away.lengthSqr() < 1.0E-4D) {
                away = new Vec3(0.0D, 1.0D, 0.0D);
            }
            Vec3 push = away.normalize().scale(0.8D);
            victim.push(push.x, 0.35D, push.z);
            victim.hurtMarked = true;
        }

        Fx.particles(level, ParticleTypes.ELECTRIC_SPARK, where, 40, DamageMath.LIGHTNING_RADIUS * 0.5D, 0.15D);
        Fx.particles(level, ParticleTypes.FLASH, where, 1, 0.0D, 0.0D);
        Fx.sound(level, where, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 1.0F, 1.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("ArsenalCharged", this.isCharged());
        compound.putBoolean("ArsenalStruck", this.struck);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setCharged(compound.getBoolean("ArsenalCharged"));
        this.struck = compound.getBoolean("ArsenalStruck");
    }
}
