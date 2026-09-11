package dev.riftal.creator.features.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.toolkit.cam.CameraBookmark;
import dev.riftal.creator.features.toolkit.cam.CameraManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** A shot list is only useful if it survives a restart with the exact angle it was saved at. */
class CameraBookmarkTest {

    private static final ResourceLocation OVERWORLD =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void roundTripsThroughNbtWithoutLosingPrecision() {
        CameraBookmark hero = new CameraBookmark("hero", OVERWORLD,
                12.53125D, 68.0D, -40.5D, 134.75F, -22.5F);
        CameraBookmark loaded = CameraBookmark.load(hero.save());
        assertEquals(hero, loaded);
        assertEquals(134.75F, loaded.yaw());
        assertEquals(-22.5F, loaded.pitch());
    }

    @Test
    void malformedTagsLoadAsNull() {
        assertNull(CameraBookmark.load(new CompoundTag()), "no name and no dimension");

        CompoundTag noName = new CameraBookmark("hero", OVERWORLD, 0, 0, 0, 0, 0).save();
        noName.putString("name", "");
        assertNull(CameraBookmark.load(noName));

        CompoundTag badDimension = new CameraBookmark("hero", OVERWORLD, 0, 0, 0, 0, 0).save();
        badDimension.putString("dimension", "Not A Dimension");
        assertNull(CameraBookmark.load(badDimension));
    }

    @Test
    void listEntryNamesThePlace() {
        CameraBookmark hero = new CameraBookmark("hero", OVERWORLD, 12.56D, 68.0D, -40.44D, 90.0F, 0.0F);
        assertEquals("hero  minecraft:overworld  12.6 / 68.0 / -40.4", hero.describe());
    }

    @Test
    void nonFiniteBookmarksAreRefusedBeforeTheyTeleportAnyone() {
        assertTrue(CameraManager.isFinite(new CameraBookmark("ok", OVERWORLD, 1, 2, 3, 4, 5)));
        assertFalse(CameraManager.isFinite(
                new CameraBookmark("nan", OVERWORLD, Double.NaN, 2, 3, 4, 5)));
        assertFalse(CameraManager.isFinite(
                new CameraBookmark("inf", OVERWORLD, 1, Double.POSITIVE_INFINITY, 3, 4, 5)));
        assertFalse(CameraManager.isFinite(
                new CameraBookmark("yaw", OVERWORLD, 1, 2, 3, Float.NaN, 5)));
    }
}
