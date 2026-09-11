package dev.riftal.creator.features.arsenal.entity;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.arsenal.ArsenalFeature;
import dev.riftal.creator.features.arsenal.mechanic.GrappleManager;
import dev.riftal.creator.features.arsenal.net.GrappleFxPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Grapple Blade's hook.
 *
 * <p>Flies out, bites the first block or entity it touches, holds station while the owner is reeled
 * to it ({@link GrappleManager}), then flies home on trident-loyalty style math and despawns.
 *
 * <p>It extends {@code ThrowableProjectile} rather than {@code Projectile} because
 * {@code Projectile}'s constructor is package-private in {@code net.minecraft.world.entity.projectile}.
 * Once attached it sets zero velocity and no gravity, so the inherited tick becomes a no-op mover
 * and all the usual base-entity ticking (water, portals, tick count) keeps running.
 */
public class GrappleHookEntity extends ThrowableProjectile {

    /** In flight, looking for something to bite. */
    public static final byte STATE_FLYING = 0;

    /** Bitten and holding station; the owner is being reeled in. */
    public static final byte STATE_ATTACHED = 1;

    /** Flying back to the owner, about to despawn. */
    public static final byte STATE_RETURNING = 2;

    /** Furthest the hook may travel from where it was fired before it gives up. */
    public static final double MAX_RANGE = 24.0D;

    /** Furthest the hook may drift from its owner before it gives up. */
    public static final double LEASH_RANGE = 32.0D;

    /** Hard lifetime, in ticks, so a hook can never be left hanging in the world. */
    public static final int MAX_LIFE = 100;

    private static final EntityDataAccessor<Byte> DATA_STATE =
            SynchedEntityData.defineId(GrappleHookEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_HOOKED_ENTITY =
            SynchedEntityData.defineId(GrappleHookEntity.class, EntityDataSerializers.INT);

    private Vec3 anchor = Vec3.ZERO;
    private Vec3 origin = Vec3.ZERO;
    private int life;

    /** Registry factory constructor. */
    public GrappleHookEntity(EntityType<? extends GrappleHookEntity> type, Level level) {
        super(type, level);
    }

    /** Fired from a player's eye position. */
    public GrappleHookEntity(Level level, LivingEntity owner) {
        super(ArsenalFeature.GRAPPLE_HOOK.get(), owner, level);
        this.origin = this.position();
        this.anchor = this.position();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, STATE_FLYING);
        builder.define(DATA_HOOKED_ENTITY, 0);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.03D;
    }

    /** Current state: one of {@link #STATE_FLYING}, {@link #STATE_ATTACHED}, {@link #STATE_RETURNING}. */
    public byte getState() {
        return this.entityData.get(DATA_STATE);
    }

    private void setState(byte state) {
        this.entityData.set(DATA_STATE, state);
    }

    /** True while the hook is biting something and the owner should be reeled in. */
    public boolean isAttached() {
        return this.getState() == STATE_ATTACHED;
    }

    /** The entity the hook bit, or null when it bit a block (or nothing). */
    public Entity getHookedEntity() {
        int id = this.entityData.get(DATA_HOOKED_ENTITY);
        return id == 0 ? null : this.level().getEntity(id);
    }

    /** The point the owner is being pulled towards. Server-authoritative. */
    public Vec3 anchor() {
        return this.anchor;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide) {
            this.serverTick();
            if (this.isRemoved()) {
                return;
            }
        }
        super.tick();
    }

    private void serverTick() {
        Entity owner = this.getOwner();
        if (!(owner instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()
                || player.level() != this.level()) {
            this.discard();
            return;
        }

        this.life++;
        byte state = this.getState();
        if (this.life > MAX_LIFE * 2) {
            // A return flight that never lands (owner sprinting away, elytra, whatever) still goes.
            this.discard();
            return;
        }
        if (this.life > MAX_LIFE && state != STATE_RETURNING) {
            this.retract();
            return;
        }

        switch (state) {
            case STATE_FLYING -> {
                if (this.position().distanceTo(this.origin) > MAX_RANGE
                        || this.position().distanceTo(player.position()) > LEASH_RANGE
                        || this.isInWater()) {
                    this.retract();
                }
            }
            case STATE_ATTACHED -> {
                Entity hooked = this.getHookedEntity();
                if (hooked != null) {
                    if (!hooked.isAlive() || hooked.level() != this.level()) {
                        this.retract();
                        return;
                    }
                    this.anchor = hooked.position().add(0.0D, hooked.getBbHeight() * 0.5D, 0.0D);
                    this.setPos(this.anchor.x, this.anchor.y, this.anchor.z);
                }
                this.setDeltaMovement(Vec3.ZERO);
                if (this.position().distanceTo(player.position()) > LEASH_RANGE) {
                    this.retract();
                }
            }
            case STATE_RETURNING -> {
                Vec3 toOwner = player.getEyePosition().subtract(this.position());
                if (toOwner.length() < 1.5D) {
                    this.discard();
                    return;
                }
                this.setDeltaMovement(this.getDeltaMovement().scale(0.95D)
                        .add(toOwner.normalize().scale(0.30D)));
            }
            default -> {
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return this.getState() == STATE_FLYING && target != this.getOwner() && super.canHitEntity(target);
    }

    /**
     * Suppresses the inherited hit handling once the hook has bitten. An attached hook is scanned
     * with a zero-length move vector every tick and a returning one flies back through geometry;
     * without this the base tick would re-fire {@code PROJECTILE_LAND} game events forever.
     */
    @Override
    protected ProjectileDeflection hitTargetOrDeflectSelf(HitResult hitResult) {
        if (this.getState() != STATE_FLYING) {
            return ProjectileDeflection.NONE;
        }
        return super.hitTargetOrDeflectSelf(hitResult);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (this.getState() != STATE_FLYING) {
            return;
        }
        super.onHitBlock(result);
        if (this.level().isClientSide) {
            return;
        }
        this.anchor = result.getLocation();
        this.attach();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.getState() != STATE_FLYING) {
            return;
        }
        super.onHitEntity(result);
        if (this.level().isClientSide) {
            return;
        }
        Entity target = result.getEntity();
        if (target == this.getOwner()) {
            return;
        }
        this.entityData.set(DATA_HOOKED_ENTITY, target.getId());
        this.anchor = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        this.attach();
    }

    private void attach() {
        this.setState(STATE_ATTACHED);
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.setPos(this.anchor.x, this.anchor.y, this.anchor.z);

        if (this.level() instanceof ServerLevel serverLevel) {
            Fx.particles(serverLevel, ParticleTypes.CRIT, this.anchor, 6, 0.15D, 0.05D);
            Fx.sound(serverLevel, this.anchor, ArsenalFeature.hookBite(), SoundSource.PLAYERS, 0.9F, 1.0F);
            Payloads.sendToTracking(this, GrappleFxPayload.of(this, GrappleFxPayload.STATE_BITE, this.anchor));
        }
        if (this.getOwner() instanceof ServerPlayer player) {
            GrappleManager.beginPull(player, this);
        }
    }

    /** Cuts the line: the hook lets go and flies home. Safe to call more than once. */
    public void retract() {
        if (this.getState() == STATE_RETURNING || this.isRemoved()) {
            return;
        }
        this.setState(STATE_RETURNING);
        this.entityData.set(DATA_HOOKED_ENTITY, 0);
        this.setNoGravity(true);
        if (this.getOwner() instanceof ServerPlayer player) {
            // stopPull, not cancelPull: the pull keeps running its fall-damage grace window after
            // the line is cut, otherwise landing lower than you started still hurts.
            GrappleManager.stopPull(player);
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            Fx.sound(serverLevel, this.position(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.6F, 1.6F);
            Payloads.sendToTracking(this, GrappleFxPayload.of(this, GrappleFxPayload.STATE_RETURN, this.position()));
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide && this.getOwner() instanceof ServerPlayer player) {
            GrappleManager.onHookRemoved(player, this);
        }
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putByte("ArsenalState", this.getState());
        compound.putInt("ArsenalLife", this.life);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setState(compound.getByte("ArsenalState"));
        this.life = compound.getInt("ArsenalLife");
        this.origin = this.position();
        this.anchor = this.position();
    }
}
