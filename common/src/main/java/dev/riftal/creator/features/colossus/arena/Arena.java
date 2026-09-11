package dev.riftal.creator.features.colossus.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * One named fighting pit: a centre and a radius. Immutable, NBT round-trippable, no registries -
 * the whole thing is unit-testable.
 */
public record Arena(String name, BlockPos centre, int radius) {

    /** Smallest radius {@code /colossus arena set} will accept. */
    public static final int MIN_RADIUS = 6;

    /** Largest radius {@code /colossus arena set} will accept. */
    public static final int MAX_RADIUS = 64;

    /** Radius used when the command does not name one. */
    public static final int DEFAULT_RADIUS = 20;

    /** Arena the boss binds to when nothing else is asked for. */
    public static final String DEFAULT_NAME = "main";

    public Arena {
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putInt("X", centre.getX());
        tag.putInt("Y", centre.getY());
        tag.putInt("Z", centre.getZ());
        tag.putInt("Radius", radius);
        return tag;
    }

    public static Arena load(CompoundTag tag) {
        return new Arena(
                tag.getString("Name"),
                new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")),
                tag.getInt("Radius"));
    }
}
