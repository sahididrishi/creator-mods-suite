package dev.riftal.creator.features.toolkit.wave;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Places a wave of mobs for the camera: a perfect ring facing inward, or a uniform scatter.
 *
 * <p>Every spawned entity carries the {@link #WAVE_TAG} scoreboard tag, which is what
 * {@code /toolkit wave clear} and {@code /toolkit arena reset} use to find them again - no entity
 * list is kept in memory, so a reload or a crash cannot leave orphans behind.
 */
public final class WaveSpawner {

    /** Scoreboard tag stamped on every entity this class spawns. */
    public static final String WAVE_TAG = "creator_toolkit_wave";

    /** Hard cap so a typo cannot spawn ten thousand zombies mid-take. */
    public static final int MAX_COUNT = 200;

    /** How far above or below the requested height a spawn point may hunt for a floor. */
    public static final int GROUND_SEARCH = 8;

    /**
     * Spawns {@code count} entities around {@code centre}.
     *
     * @return how many actually spawned; entity types that refuse to spawn are skipped
     */
    public static int spawn(ServerLevel level, Vec3 centre, EntityType<?> type, int count,
                            double radius, WaveMath.Mode mode) {
        int wanted = Math.max(0, Math.min(MAX_COUNT, count));
        double r = Math.max(0.0D, radius);
        RandomSource random = level.getRandom();
        int spawned = 0;
        for (int i = 0; i < wanted; i++) {
            double offsetX;
            double offsetZ;
            if (mode == WaveMath.Mode.RING) {
                double angle = WaveMath.ringAngle(i, wanted);
                offsetX = WaveMath.offsetX(angle, r);
                offsetZ = WaveMath.offsetZ(angle, r);
            } else {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double scattered = WaveMath.discRadius(random.nextDouble(), r);
                offsetX = Math.cos(angle) * scattered;
                offsetZ = Math.sin(angle) * scattered;
            }
            BlockPos ground = groundNear(level, centre.x + offsetX, centre.y, centre.z + offsetZ);
            Entity entity = type.spawn(level, ground, MobSpawnType.COMMAND);
            if (entity == null) {
                continue;
            }
            float yaw = WaveMath.yawTowardCentre(offsetX, offsetZ);
            entity.moveTo(ground.getX() + 0.5D, ground.getY(), ground.getZ() + 0.5D, yaw, 0.0F);
            entity.setYHeadRot(yaw);
            entity.setYBodyRot(yaw);
            entity.addTag(WAVE_TAG);
            if (entity instanceof Mob mob) {
                mob.setPersistenceRequired();
            }
            spawned++;
        }
        return spawned;
    }

    /**
     * The block a wave mob should stand on at {@code (x, z)}, starting from the height the director
     * asked for.
     *
     * <p>Deliberately <em>not</em> the world heightmap: that is the top of the sky, so a wave called
     * for under any kind of roof - a cave set, a walled arena, a GameTest box with its barrier lid -
     * would land the whole ring above the roof instead of on the floor next to the camera. Instead
     * this climbs out of terrain and then falls to the first floor within {@link #GROUND_SEARCH}
     * blocks, and keeps the requested height when there is nothing to stand on nearby.
     */
    public static BlockPos groundNear(ServerLevel level, double x, double y, double z) {
        BlockPos wanted = BlockPos.containing(x, y, z);
        BlockPos.MutableBlockPos cursor = wanted.mutable();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        // Asked for a spot inside terrain: climb out, but no further than the search window.
        int climbed = 0;
        while (climbed < GROUND_SEARCH
                && cursor.getY() < level.getMaxBuildHeight() - 1
                && blocksMotion(level, probe.set(cursor.getX(), cursor.getY(), cursor.getZ()))) {
            cursor.move(0, 1, 0);
            climbed++;
        }

        // Asked for a spot in mid-air: fall to the first block that would hold the mob up.
        int dropped = 0;
        while (dropped < GROUND_SEARCH
                && cursor.getY() > level.getMinBuildHeight()
                && !blocksMotion(level, probe.set(cursor.getX(), cursor.getY() - 1, cursor.getZ()))) {
            cursor.move(0, -1, 0);
            dropped++;
        }

        // Over a void or a very deep drop: the height that was asked for beats a long fall.
        if (dropped >= GROUND_SEARCH
                && !blocksMotion(level, probe.set(cursor.getX(), cursor.getY() - 1, cursor.getZ()))) {
            return wanted;
        }
        return cursor.immutable();
    }

    private static boolean blocksMotion(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.blocksMotion();
    }

    /** Removes every wave entity in one level. Returns how many were discarded. */
    public static int clear(ServerLevel level) {
        List<Entity> doomed = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity.getTags().contains(WAVE_TAG)) {
                doomed.add(entity);
            }
        }
        for (Entity entity : doomed) {
            entity.discard();
        }
        return doomed.size();
    }

    private WaveSpawner() {
    }
}
