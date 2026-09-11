package dev.riftal.creator.features.toolkit.cam;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * A saved camera position: exactly where the director stood and exactly where they looked.
 *
 * <p>Server-side and world-scoped, so every crew member can jump to the same shot with
 * {@code /toolkit cam go hero}.
 *
 * @param name      bookmark name
 * @param dimension the level it was saved in
 * @param x         precise position
 * @param y         precise position
 * @param z         precise position
 * @param yaw       exact yaw, degrees
 * @param pitch     exact pitch, degrees
 */
public record CameraBookmark(String name, ResourceLocation dimension,
                             double x, double y, double z, float yaw, float pitch) {

    /** Serialises into a fresh tag. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("dimension", dimension.toString());
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        return tag;
    }

    /** Reads a bookmark written by {@link #save()}, or null if the tag is malformed. */
    public static CameraBookmark load(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        String name = tag.getString("name");
        if (dimension == null || name.isEmpty()) {
            return null;
        }
        return new CameraBookmark(name, dimension, tag.getDouble("x"), tag.getDouble("y"),
                tag.getDouble("z"), tag.getFloat("yaw"), tag.getFloat("pitch"));
    }

    /** {@code hero  overworld  12.5 / 68.0 / -40.5}, for {@code /toolkit cam list}. */
    public String describe() {
        return String.format(java.util.Locale.ROOT, "%s  %s  %.1f / %.1f / %.1f",
                name, dimension, x, y, z);
    }
}
