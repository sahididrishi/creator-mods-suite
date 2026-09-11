package dev.riftal.creator.features.events.events;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.core.util.Titles;
import dev.riftal.creator.features.events.EventManager;
import dev.riftal.creator.features.events.EventsFeature;
import dev.riftal.creator.features.events.api.EventContext;
import dev.riftal.creator.features.events.api.EventPhase;
import dev.riftal.creator.features.events.api.StopReason;
import dev.riftal.creator.features.events.api.WorldEvent;
import dev.riftal.creator.features.events.util.CraterShape;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * {@code meteor} - a five second countdown, a burning boulder on a straight line, a crater with a
 * loot chest in the middle of it.
 *
 * <p>The explosion lives in {@code onPhaseStart("impact")} rather than in the entity, so
 * {@code /event skip} out of the countdown or the flight still produces the money shot.
 */
public final class MeteorEvent implements WorldEvent {

    /** Countdown length: five seconds of title cards. */
    public static final int COUNTDOWN_TICKS = 100;

    /** Flight length. The spawn offset and speed are tuned so the boulder arrives inside this. */
    public static final int FLIGHT_TICKS = 60;

    /** Blocks per tick along the approach vector. */
    public static final double SPEED = 1.8D;

    /** Where the boulder is spawned, relative to the target. */
    public static final Vec3 SPAWN_OFFSET = new Vec3(-40.0D, 60.0D, -40.0D);

    private static final float EXPLOSION_RADIUS = 6.0F;
    private static final int CRATER_RADIUS = 5;

    /** The chest's loot table, filled by {@code data/creator_events/loot_table/meteor.json}. */
    public static final ResourceKey<LootTable> METEOR_LOOT =
            ResourceKey.create(Registries.LOOT_TABLE,
                    net.minecraft.resources.ResourceLocation
                            .fromNamespaceAndPath(EventsFeature.NAMESPACE, "meteor"));

    private UUID meteorId;
    private BlockPos craterCentre;
    private boolean impacted;
    private boolean flightLost;
    /** Where the boulder actually arrived, or null when it never got there (a skip, or a restart). */
    private Vec3 arrivalPos;

    @Override
    public String id() {
        return "meteor";
    }

    @Override
    public List<EventPhase> phases() {
        return List.of(
                EventPhase.ticks("countdown", COUNTDOWN_TICKS),
                EventPhase.ticks("flight", FLIGHT_TICKS),
                EventPhase.ticks("impact", 1),
                EventPhase.ticks("aftermath", 400));
    }

    @Override
    public void onStart(EventContext ctx, boolean resumed) {
        if (resumed && meteorId != null && ctx.level().getEntity(meteorId) == null) {
            // The boulder did not survive the restart. Fall straight through to the impact.
            flightLost = true;
        }
    }

    @Override
    public void onPhaseStart(EventContext ctx, EventPhase phase, int phaseIndex) {
        switch (phase.id()) {
            case "flight" -> spawnMeteor(ctx);
            case "impact" -> impact(ctx);
            default -> {
            }
        }
    }

    @Override
    public void tick(EventContext ctx, EventPhase phase, int phaseTick) {
        switch (phase.id()) {
            case "countdown" -> countdown(ctx, phaseTick);
            case "flight" -> flight(ctx);
            default -> {
            }
        }
    }

    @Override
    public boolean isPhaseComplete(EventContext ctx, EventPhase phase, int phaseTick) {
        if ("flight".equals(phase.id())) {
            if (flightLost) {
                return true;
            }
            Entity meteor = meteorId == null ? null : ctx.level().getEntity(meteorId);
            if (meteor == null || meteor.isRemoved()) {
                return true;
            }
            if (hasArrived(ctx, meteor)) {
                // Remember where it actually got to before discarding it: ServerLevel#getEntity
                // stops resolving a discarded entity, and the impact has to land on this spot and
                // not on the spawn point 40 blocks up-range.
                arrivalPos = meteor.position();
                meteor.discard();
                return true;
            }
        }
        return WorldEvent.super.isPhaseComplete(ctx, phase, phaseTick);
    }

    @Override
    public void onStop(EventContext ctx, StopReason reason) {
        if (ctx != null && meteorId != null) {
            Entity meteor = ctx.level().getEntity(meteorId);
            if (meteor != null) {
                meteor.discard();
            }
        }
        meteorId = null;
        TickScheduler.cancelAll(EventManager.tag("meteor"));
    }

    @Override
    public float progress(EventContext ctx, EventPhase phase, int phaseTick) {
        return WorldEvent.super.progress(ctx, phase, phaseTick);
    }

    @Override
    public void save(CompoundTag tag) {
        if (meteorId != null) {
            tag.putUUID("meteor", meteorId);
        }
        tag.putBoolean("impacted", impacted);
        if (arrivalPos != null) {
            tag.putDouble("arrival_x", arrivalPos.x);
            tag.putDouble("arrival_y", arrivalPos.y);
            tag.putDouble("arrival_z", arrivalPos.z);
        }
        if (craterCentre != null) {
            tag.putInt("crater_x", craterCentre.getX());
            tag.putInt("crater_y", craterCentre.getY());
            tag.putInt("crater_z", craterCentre.getZ());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        meteorId = tag.hasUUID("meteor") ? tag.getUUID("meteor") : null;
        impacted = tag.getBoolean("impacted");
        arrivalPos = tag.contains("arrival_x")
                ? new Vec3(tag.getDouble("arrival_x"), tag.getDouble("arrival_y"),
                        tag.getDouble("arrival_z"))
                : null;
        if (tag.contains("crater_x")) {
            craterCentre = new BlockPos(tag.getInt("crater_x"), tag.getInt("crater_y"),
                    tag.getInt("crater_z"));
        }
    }

    private void countdown(EventContext ctx, int phaseTick) {
        ServerLevel level = ctx.level();
        if (phaseTick % 5 == 0) {
            Fx.particleRing(level, ParticleTypes.FLAME, ctx.origin().add(0.0D, 0.5D, 0.0D), 3.0D, 24);
        }
        if (phaseTick % 20 != 1) {
            return;
        }
        int secondsLeft = Math.max(1, (COUNTDOWN_TICKS - phaseTick + 1 + 19) / 20);
        Component title = Component.translatable("title.creator_events.meteor")
                .withStyle(ChatFormatting.RED);
        Component subtitle = Component.literal(Integer.toString(secondsLeft))
                .withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : ctx.players()) {
            Titles.show(player, title, subtitle, 0, 15, 5);
        }
        float pitch = 0.8F + (5 - Math.min(5, secondsLeft)) * 0.2F;
        Fx.sound(level, ctx.origin(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER,
                1.0F, pitch);
    }

    private void spawnMeteor(EventContext ctx) {
        ServerLevel level = ctx.level();
        Vec3 target = ctx.origin();
        Vec3 start = target.add(SPAWN_OFFSET);
        MeteorSpawn spawn = MeteorSpawn.of(start, target);

        var entity = EventsFeature.meteorType().create(level);
        if (entity == null) {
            LOG.warn("[events] meteor entity type refused to create; skipping straight to impact");
            flightLost = true;
            return;
        }
        entity.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
        entity.setTarget(target);
        entity.setNoGravity(true);
        entity.setDeltaMovement(spawn.velocity());
        level.addFreshEntity(entity);
        meteorId = entity.getUUID();
        Fx.sound(level, start, EventsFeature.meteorWhistle(), SoundSource.HOSTILE, 3.0F, 1.0F);
    }

    private void flight(EventContext ctx) {
        if (meteorId == null) {
            return;
        }
        Entity meteor = ctx.level().getEntity(meteorId);
        if (meteor == null) {
            flightLost = true;
        }
    }

    private boolean hasArrived(EventContext ctx, Entity meteor) {
        Vec3 target = ctx.origin();
        if (meteor.getY() <= target.y) {
            return true;
        }
        BlockState state = ctx.level().getBlockState(meteor.blockPosition());
        return state.isSolid();
    }

    private void impact(EventContext ctx) {
        if (impacted) {
            return;
        }
        impacted = true;
        ServerLevel level = ctx.level();
        // The boulder's own arrival point when it flew the whole way; the director's aim point when
        // the flight was skipped, cut short by a restart, or never started at all. Using the live
        // entity position here would drop the crater wherever the boulder happened to be, which for
        // a skip is 40 blocks out and 60 blocks up.
        Vec3 target = arrivalPos != null ? arrivalPos : ctx.origin();
        if (meteorId != null) {
            Entity meteor = level.getEntity(meteorId);
            if (meteor != null) {
                meteor.discard();
            }
            meteorId = null;
        }
        BlockPos centre = BlockPos.containing(target);
        craterCentre = centre;

        level.explode(null, target.x, target.y, target.z, EXPLOSION_RADIUS, true,
                Level.ExplosionInteraction.TNT);
        Fx.sound(level, target, EventsFeature.meteorImpact(), SoundSource.HOSTILE, 4.0F, 0.8F);
        Fx.particles(level, ParticleTypes.EXPLOSION_EMITTER, target, 6, 3.0D, 0.0D);

        // One tick later, so the explosion cannot eat the chest we are about to place.
        TickScheduler.runLater(1, () -> carveCrater(level, centre)).tag(EventManager.tag("meteor"));
    }

    private void carveCrater(ServerLevel level, BlockPos centre) {
        RandomSource random = level.getRandom();
        for (CraterShape.Offset offset : CraterShape.inside(CRATER_RADIUS)) {
            BlockPos pos = centre.offset(offset.x(), offset.y(), offset.z());
            if (pos.getY() <= level.getMinBuildHeight() + 1) {
                continue;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        for (CraterShape.Offset offset : CraterShape.rim(CRATER_RADIUS)) {
            BlockPos pos = centre.offset(offset.x(), offset.y(), offset.z());
            if (pos.getY() <= level.getMinBuildHeight() + 1) {
                continue;
            }
            if (level.getBlockState(pos.below()).isAir() && level.getBlockState(pos).isAir()) {
                continue;
            }
            int roll = random.nextInt(10);
            BlockState scorched = roll < 3
                    ? Blocks.MAGMA_BLOCK.defaultBlockState()
                    : (roll < 7 ? Blocks.NETHERRACK.defaultBlockState()
                    : Blocks.BLACKSTONE.defaultBlockState());
            level.setBlock(pos, scorched, 3);
            if (offset.y() >= 0 && random.nextFloat() < 0.3F
                    && level.getBlockState(pos.above()).isAir()) {
                level.setBlock(pos.above(), Blocks.FIRE.defaultBlockState(), 3);
            }
        }

        level.setBlock(centre, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(centre.below(), Blocks.BLACKSTONE.defaultBlockState(), 3);
        level.setBlock(centre, Blocks.CHEST.defaultBlockState(), 3);
        RandomizableContainer.setBlockEntityLootTable(level, random, centre, METEOR_LOOT);
        LOG.info("[events] meteor crater at {} {} {}", centre.getX(), centre.getY(), centre.getZ());
    }

    /** The straight-line approach, split out so {@code MeteorTrajectoryTest} can check it. */
    public record MeteorSpawn(Vec3 start, Vec3 target, Vec3 velocity) {

        /** Normalised approach at {@link MeteorEvent#SPEED} blocks per tick. */
        public static MeteorSpawn of(Vec3 start, Vec3 target) {
            Vec3 delta = target.subtract(start);
            Vec3 velocity = delta.lengthSqr() < 1.0E-6D
                    ? new Vec3(0.0D, -SPEED, 0.0D)
                    : delta.normalize().scale(SPEED);
            return new MeteorSpawn(start, target, velocity);
        }

        /** Ticks the boulder needs to cover the distance. */
        public int travelTicks() {
            return (int) Math.ceil(target.subtract(start).length() / SPEED);
        }
    }
}
