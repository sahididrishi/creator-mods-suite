package dev.riftal.creator.features.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.toolkit.arena.ArenaSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The arena snapshot is what {@code /toolkit arena reset} replays, and it round-trips through the
 * world's {@code SavedData} file - so its NBT shape is the part that has to be right.
 */
class ArenaSnapshotTest {

    private static ArenaSnapshot sample() {
        CompoundTag blocks = new CompoundTag();
        blocks.putInt("DataVersion", 3955);
        ListTag palette = new ListTag();
        CompoundTag stone = new CompoundTag();
        stone.putString("Name", "minecraft:stone");
        palette.add(stone);
        blocks.put("palette", palette);

        CompoundTag zombie = new CompoundTag();
        zombie.putString("id", "minecraft:zombie");
        zombie.putString("CustomName", "\"A\"");

        return new ArenaSnapshot("ring", ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                new BlockPos(10, 60, -20), new Vec3i(10, 4, 10), blocks, List.of(zombie), 1_700_000_000_000L);
    }

    @Test
    void describesItsBox() {
        ArenaSnapshot snapshot = sample();
        assertEquals("10x4x10", snapshot.sizeText());
        assertEquals(400L, snapshot.volume());
        // Inclusive corners: a 10-wide box starting at x=10 ends at x=19.
        assertEquals(new BlockPos(19, 63, -11), snapshot.max());
    }

    @Test
    void roundTripsThroughNbt() {
        ArenaSnapshot original = sample();
        ArenaSnapshot loaded = ArenaSnapshot.load(original.save());

        assertEquals(original.name(), loaded.name());
        assertEquals(original.dimension(), loaded.dimension());
        assertEquals(original.origin(), loaded.origin());
        assertEquals(original.size(), loaded.size());
        assertEquals(original.savedAtEpochMs(), loaded.savedAtEpochMs());
        assertEquals(original.blocks(), loaded.blocks());
        assertEquals(1, loaded.entities().size());
        assertEquals("minecraft:zombie", loaded.entities().get(0).getString("id"));
    }

    @Test
    void handsOutCopiesOfTheBlockTag() {
        ArenaSnapshot snapshot = sample();
        CompoundTag copy = snapshot.blocksCopy();
        assertNotSame(snapshot.blocks(), copy);
        copy.putString("vandalised", "yes");
        assertTrue(snapshot.blocks().getString("vandalised").isEmpty(),
                "StructureTemplate#load must not be able to mutate the stored snapshot");
    }

    @Test
    void entityListIsImmutable() {
        List<CompoundTag> mutable = new ArrayList<>();
        mutable.add(new CompoundTag());
        ArenaSnapshot snapshot = new ArenaSnapshot("a",
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                BlockPos.ZERO, new Vec3i(1, 1, 1), new CompoundTag(), mutable, 0L);
        mutable.add(new CompoundTag());
        assertEquals(1, snapshot.entities().size(), "the snapshot copied the list it was given");
        assertThrows(UnsupportedOperationException.class, () -> snapshot.entities().add(new CompoundTag()));
    }

    @Test
    void malformedTagsLoadAsNull() {
        assertNull(ArenaSnapshot.load(new CompoundTag()), "no origin or size");

        CompoundTag shortOrigin = sample().save();
        shortOrigin.putIntArray("origin", new int[] {1, 2});
        assertNull(ArenaSnapshot.load(shortOrigin), "a two-element origin is not a position");

        CompoundTag badDimension = sample().save();
        badDimension.putString("dimension", "NOT A DIMENSION");
        assertNull(ArenaSnapshot.load(badDimension));
    }
}
