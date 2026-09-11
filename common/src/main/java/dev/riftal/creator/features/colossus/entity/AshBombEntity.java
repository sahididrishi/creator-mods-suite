package dev.riftal.creator.features.colossus.entity;

import dev.riftal.creator.features.colossus.ColossusFeature;
import dev.riftal.creator.features.colossus.Combatants;
import dev.riftal.creator.features.colossus.FirePatches;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The lump of burning slag the Colossus rains down in phase 2.
 *
 * <p>Hits for {@value #IMPACT_DAMAGE} and sets its victim alight; on the floor it leaves a 3x3
 * patch of fire that puts itself out again after {@link FirePatches#DEFAULT_LIFETIME_TICKS} ticks,
 * so a repeated take never leaves the arena permanently burning.
 */
public class AshBombEntity extends ThrowableItemProjectile {

    /** Direct hit damage. */
    public static final float IMPACT_DAMAGE = 6.0F;

    /** How long a victim burns for, in ticks. */
    public static final int IGNITE_TICKS = 60;

    /** Half-width of the fire patch left on impact. */
    public static final int PATCH_RADIUS = 1;

    public AshBombEntity(EntityType<? extends AshBombEntity> entityType, Level level) {
        super(entityType, level);
    }

    public AshBombEntity(Level level, LivingEntity thrower) {
        super(ColossusFeature.ashBomb().get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.MAGMA_CREAM;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (target instanceof AshenColossusEntity || target instanceof AshenMinionEntity) {
            return false;
        }
        // setRemainingFireTicks in onHitEntity bypasses creative mode, so a bomb must not be
        // allowed to reach a camera at all.
        if (Combatants.isCamera(target)) {
            return false;
        }
        return super.canHitEntity(target);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level && this.tickCount % 2 == 0) {
            level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(),
                    2, 0.08D, 0.08D, 0.08D, 0.0D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(), this.getZ(),
                    1, 0.1D, 0.1D, 0.1D, 0.0D);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity victim = result.getEntity();
        Entity owner = this.getOwner();
        victim.hurt(this.damageSources().mobProjectile(this,
                owner instanceof LivingEntity living ? living : null), IMPACT_DAMAGE);
        victim.setRemainingFireTicks(IGNITE_TICKS);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level() instanceof ServerLevel level
                && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            FirePatches.scatter(level, result.getBlockPos().above(), PATCH_RADIUS,
                    FirePatches.DEFAULT_LIFETIME_TICKS);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY(), this.getZ(),
                    12, 0.3D, 0.2D, 0.3D, 0.05D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(), this.getZ(),
                    10, 0.4D, 0.3D, 0.4D, 0.02D);
            this.discard();
        }
    }
}
