package dev.riftal.creator.features.toolkit.arena;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.features.toolkit.wave.WaveSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Block-for-block arena snapshots: {@code /toolkit arena save ring 10 60 10 20 66 20} captures the
 * volume and everything standing in it, and {@code /toolkit arena reset ring} puts it all back.
 *
 * <p>Blocks travel as a vanilla {@code StructureTemplate} tag; entities as one
 * {@code Entity#saveAsPassenger} tag each. Players are never captured and never removed - an actor
 * standing in the arena during a reset keeps their inventory, their position and their life.
 */
public final class ArenaManager {

    /**
     * Largest volume we will capture. A {@code StructureTemplate} holds every block in memory, so
     * this is the line between "instant" and "the server hitches on camera".
     */
    public static final long MAX_VOLUME = 512_000L;

    /** Result of a save attempt. */
    public record SaveResult(ArenaSnapshot snapshot, String error) {

        /** True when the snapshot was taken. */
        public boolean ok() {
            return snapshot != null;
        }
    }

    /**
     * Captures the inclusive box between {@code from} and {@code to}.
     *
     * @return the snapshot, or a result carrying a human-readable error key argument
     */
    public static SaveResult save(ServerLevel level, String name, BlockPos from, BlockPos to) {
        BlockPos min = new BlockPos(Math.min(from.getX(), to.getX()),
                Math.min(from.getY(), to.getY()),
                Math.min(from.getZ(), to.getZ()));
        BlockPos max = new BlockPos(Math.max(from.getX(), to.getX()),
                Math.max(from.getY(), to.getY()),
                Math.max(from.getZ(), to.getZ()));
        Vec3i size = new Vec3i(max.getX() - min.getX() + 1,
                max.getY() - min.getY() + 1,
                max.getZ() - min.getZ() + 1);
        long volume = (long) size.getX() * size.getY() * size.getZ();
        if (volume > MAX_VOLUME) {
            return new SaveResult(null, volume + " blocks, limit " + MAX_VOLUME);
        }

        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, min, size, false, Blocks.STRUCTURE_VOID);
        CompoundTag blocks = template.save(new CompoundTag());

        List<CompoundTag> entities = new ArrayList<>();
        for (Entity entity : level.getEntities((Entity) null, boxOf(min, max), e -> !(e instanceof Player))) {
            CompoundTag tag = new CompoundTag();
            if (entity.saveAsPassenger(tag)) {
                entities.add(tag);
            }
        }

        ArenaSnapshot snapshot = new ArenaSnapshot(name, level.dimension().location(), min, size,
                blocks, entities, System.currentTimeMillis());
        LOG.info("[toolkit] arena '{}' saved: {} blocks, {} entities", name, volume, entities.size());
        return new SaveResult(snapshot, null);
    }

    /** How many entities a reset removed and re-created. */
    public record ResetResult(int removed, int restored) {
    }

    /**
     * Puts the world back the way {@code snapshot} found it: every non-player entity in the box (and
     * every wave entity anywhere in the level) is discarded, the blocks are replaced, then the saved
     * entities are re-created.
     */
    public static ResetResult reset(ServerLevel level, ArenaSnapshot snapshot) {
        BlockPos min = snapshot.origin();
        BlockPos max = snapshot.max();

        List<Entity> doomed = new ArrayList<>(
                level.getEntities((Entity) null, boxOf(min, max), e -> !(e instanceof Player)));
        for (Entity entity : level.getAllEntities()) {
            if (entity.getTags().contains(WaveSpawner.WAVE_TAG) && !doomed.contains(entity)) {
                doomed.add(entity);
            }
        }
        for (Entity entity : doomed) {
            entity.discard();
        }

        HolderLookup<Block> blockLookup = level.holderLookup(Registries.BLOCK);
        StructureTemplate template = new StructureTemplate();
        template.load(blockLookup, snapshot.blocksCopy());
        // UPDATE_CLIENTS only: UPDATE_NEIGHBORS would re-run water, gravel and redstone physics and
        // the arena would settle differently every reset.
        template.placeInWorld(level, min, min,
                new StructurePlaceSettings().setIgnoreEntities(true),
                level.getRandom(), Block.UPDATE_CLIENTS);

        int restored = 0;
        for (CompoundTag saved : snapshot.entities()) {
            if (respawn(level, saved.copy())) {
                restored++;
            }
        }
        LOG.info("[toolkit] arena '{}' reset: {} removed, {} restored", snapshot.name(),
                doomed.size(), restored);
        return new ResetResult(doomed.size(), restored);
    }

    private static boolean respawn(ServerLevel level, CompoundTag tag) {
        Entity entity = EntityType.loadEntityRecursive(tag, level, created -> created);
        if (entity == null) {
            return false;
        }
        if (level.addFreshEntity(entity)) {
            return true;
        }
        // addFreshEntity refuses a UUID that is already loaded. The old one is gone by now in the
        // normal case; if it is not, a fresh UUID is better than a silently missing prop.
        entity.setUUID(UUID.randomUUID());
        return level.addFreshEntity(entity);
    }

    private static AABB boxOf(BlockPos min, BlockPos max) {
        return new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
    }

    private ArenaManager() {
    }
}
