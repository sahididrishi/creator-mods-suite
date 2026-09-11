package dev.riftal.creator.features.toolkit.wave;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
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
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(centre.x + offsetX, centre.y, centre.z + offsetZ));
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
