package dev.riftal.creator.features.toolkit.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * One saved arena: the block volume as a {@code StructureTemplate} tag plus the NBT of every
 * non-player entity that was standing in it.
 *
 * <p>Immutable. {@link #blocks()} and {@link #entities()} hand out defensive copies, because the
 * component map of a {@code SavedData} must never be mutated in place.
 *
 * @param name          arena name, as typed by the director
 * @param dimension     the level the arena was captured in
 * @param origin        lowest corner of the captured box
 * @param size          box size in blocks, always positive on every axis
 * @param blocks        {@code StructureTemplate#save} output
 * @param entities      one {@code Entity#saveAsPassenger} tag per captured entity
 * @param savedAtEpochMs wall-clock time of the capture
 */
public record ArenaSnapshot(String name, ResourceLocation dimension, BlockPos origin, Vec3i size,
                            CompoundTag blocks, List<CompoundTag> entities, long savedAtEpochMs) {

    public ArenaSnapshot {
        entities = List.copyOf(entities);
    }

    /** A fresh copy of the block template tag, safe to hand to {@code StructureTemplate#load}. */
    public CompoundTag blocksCopy() {
        return blocks.copy();
    }

    /** Highest corner of the captured box, inclusive. */
    public BlockPos max() {
        return origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
    }

    /** Blocks in the captured volume. */
    public long volume() {
        return (long) size.getX() * size.getY() * size.getZ();
    }

    /** {@code 10x4x10}, for command feedback. */
    public String sizeText() {
        return size.getX() + "x" + size.getY() + "x" + size.getZ();
    }

    /** Serialises into a fresh tag. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("dimension", dimension.toString());
        tag.putIntArray("origin", new int[] {origin.getX(), origin.getY(), origin.getZ()});
        tag.putIntArray("size", new int[] {size.getX(), size.getY(), size.getZ()});
        tag.put("blocks", blocks.copy());
        ListTag entityList = new ListTag();
        for (CompoundTag entity : entities) {
            entityList.add(entity.copy());
        }
        tag.put("entities", entityList);
        tag.putLong("savedAt", savedAtEpochMs);
        return tag;
    }

    /** Reads a snapshot written by {@link #save()}, or null if the tag is malformed. */
    public static ArenaSnapshot load(CompoundTag tag) {
        int[] origin = tag.getIntArray("origin");
        int[] size = tag.getIntArray("size");
        if (origin.length != 3 || size.length != 3) {
            return null;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimension == null) {
            return null;
        }
        List<CompoundTag> entities = new ArrayList<>();
        ListTag entityList = tag.getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entityList.size(); i++) {
            entities.add(entityList.getCompound(i));
        }
        return new ArenaSnapshot(tag.getString("name"), dimension,
                new BlockPos(origin[0], origin[1], origin[2]),
                new Vec3i(size[0], size[1], size[2]),
                tag.getCompound("blocks"), entities, tag.getLong("savedAt"));
    }
}
