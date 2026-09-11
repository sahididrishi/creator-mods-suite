package dev.riftal.creator.features.events.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The burning boulder of {@code /event start meteor}.
 *
 * <p>Deliberately dumb: it flies in a straight line and trails fire. {@code MeteorEvent} owns the
 * impact decision, because the event also has to work when the director skips the flight phase and
 * no entity was ever in the air.
 */
public class MeteorEntity extends Entity {

    private static final String KEY_TARGET_X = "target_x";
    private static final String KEY_TARGET_Y = "target_y";
    private static final String KEY_TARGET_Z = "target_z";

    private Vec3 target = Vec3.ZERO;

    public MeteorEntity(EntityType<? extends MeteorEntity> entityType, Level level) {
        super(entityType, level);
        this.noCulling = true;
        this.setNoGravity(true);
    }

    /** Where the boulder is heading. Used by the event to decide it has arrived. */
    public void setTarget(Vec3 target) {
        this.target = target;
    }

    public Vec3 getTarget() {
        return target;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // No synched data: position and velocity are enough for the renderer.
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = this.getDeltaMovement();
        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);

        if (this.level() instanceof ServerLevel serverLevel) {
            Vec3 pos = this.position();
            serverLevel.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 8,
                    0.3D, 0.3D, 0.3D, 0.01D);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 6,
                    0.4D, 0.4D, 0.4D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 2,
                    0.2D, 0.2D, 0.2D, 0.0D);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.target = new Vec3(compound.getDouble(KEY_TARGET_X),
                compound.getDouble(KEY_TARGET_Y),
                compound.getDouble(KEY_TARGET_Z));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putDouble(KEY_TARGET_X, target.x);
        compound.putDouble(KEY_TARGET_Y, target.y);
        compound.putDouble(KEY_TARGET_Z, target.z);
    }
}
