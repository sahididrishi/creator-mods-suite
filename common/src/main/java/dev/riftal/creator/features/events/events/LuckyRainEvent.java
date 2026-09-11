package dev.riftal.creator.features.events.events;

import dev.riftal.creator.core.util.Selection;
import dev.riftal.creator.features.events.EventsFeature;
import dev.riftal.creator.features.events.api.EventContext;
import dev.riftal.creator.features.events.api.EventPhase;
import dev.riftal.creator.features.events.api.StopReason;
import dev.riftal.creator.features.events.api.WorldEvent;
import dev.riftal.creator.features.events.util.SpawnRing;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * {@code luckyrain} - gold "?" blocks fall around every player and burst into a random outcome.
 *
 * <p>Options: {@code interval} (ticks between drops, default 20), {@code radius} (default 12),
 * {@code duration} (seconds, default 60).
 */
public final class LuckyRainEvent implements WorldEvent {

    /** Default gap between drops, per player. */
    public static final int DEFAULT_INTERVAL = 20;

    /** Default drop radius around each player. */
    public static final double DEFAULT_RADIUS = 12.0D;

    /** How high above the surface a block starts. Well under the 600-tick despawn. */
    public static final int DROP_HEIGHT = 20;

    /** Hard ceiling on concurrent falling blocks, so a long take cannot flood the level. */
    public static final int MAX_CONCURRENT = 60;

    private int interval = DEFAULT_INTERVAL;
    private double radius = DEFAULT_RADIUS;
    private int durationTicks = 1200;

    @Override
    public String id() {
        return "luckyrain";
    }

    @Override
    public List<EventPhase> phases() {
        return List.of(EventPhase.ticks("rain", durationTicks));
    }

    @Override
    public void onStart(EventContext ctx, boolean resumed) {
        interval = ctx.options().getInt("interval", DEFAULT_INTERVAL, 2, 200);
        radius = ctx.options().getDouble("radius", DEFAULT_RADIUS, 2.0D, 48.0D);
        durationTicks = ctx.options().getInt("duration", 60, 1, 3600) * 20;
    }

    @Override
    public void tick(EventContext ctx, EventPhase phase, int phaseTick) {
        if (phaseTick % interval != 0) {
            return;
        }
        ServerLevel level = ctx.level();
        if (concurrentBlocks(ctx) >= MAX_CONCURRENT) {
            return;
        }
        RandomSource random = ctx.random();
        for (ServerPlayer player : ctx.players()) {
            if (player.isSpectator()) {
                continue;
            }
            dropOne(level, player.position(), random);
        }
    }

    @Override
    public boolean isPhaseComplete(EventContext ctx, EventPhase phase, int phaseTick) {
        return phaseTick >= durationTicks;
    }

    @Override
    public void onStop(EventContext ctx, StopReason reason) {
        // Nothing to undo: the blocks resolve themselves when they land, and disableDrop() means
        // vanilla never places one in the world.
    }

    @Override
    public float progress(EventContext ctx, EventPhase phase, int phaseTick) {
        return Math.min(1.0F, (float) phaseTick / Math.max(1, durationTicks));
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt("interval", interval);
        tag.putDouble("radius", radius);
        tag.putInt("duration", durationTicks);
    }

    @Override
    public void load(CompoundTag tag) {
        interval = Math.max(2, tag.getInt("interval"));
        radius = Math.max(2.0D, tag.getDouble("radius"));
        durationTicks = Math.max(20, tag.getInt("duration"));
    }

    private void dropOne(ServerLevel level, Vec3 centre, RandomSource random) {
        Vec3 flat = SpawnRing.sample(random, centre, 0.0D, radius);
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(flat.x, centre.y, flat.z));
        int y = Math.min(ground.getY() + DROP_HEIGHT, level.getMaxBuildHeight() - 2);
        BlockPos spawn = new BlockPos(ground.getX(), y, ground.getZ());
        if (!level.hasChunkAt(spawn) || !level.getBlockState(spawn).isAir()) {
            return;
        }
        FallingBlockEntity falling = FallingBlockEntity.fall(level, spawn,
                EventsFeature.luckyRainBlock().defaultBlockState());
        falling.dropItem = false;
        falling.disableDrop();
    }

    private int concurrentBlocks(EventContext ctx) {
        int total = 0;
        for (ServerPlayer player : ctx.players()) {
            total += Selection.around(ctx.level(), FallingBlockEntity.class, player.position(),
                    radius + DROP_HEIGHT,
                    entity -> entity.getBlockState().is(EventsFeature.luckyRainBlock())).size();
        }
        return total;
    }
}
