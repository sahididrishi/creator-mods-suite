package dev.riftal.creator.features.colossus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.riftal.creator.features.colossus.arena.Arena;
import dev.riftal.creator.features.colossus.arena.ArenaSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * Arenas persist to {@code creator_colossus_arenas.dat} and are what a creator sets up before a
 * shoot. A round trip that silently loses the radius means the ring closes on the wrong circle the
 * next time the world is loaded, which is exactly the kind of bug that only shows up on camera.
 */
class ArenaTest {

    @Test
    void nbtRoundTripKeepsEveryField() {
        Arena original = new Arena("main", new BlockPos(-120, 71, 3400), 24);
        Arena restored = Arena.load(original.save());
        assertEquals(original, restored);
        assertEquals("main", restored.name());
        assertEquals(new BlockPos(-120, 71, 3400), restored.centre());
        assertEquals(24, restored.radius());
    }

    @Test
    void theSavedTagUsesTheDocumentedKeys() {
        CompoundTag tag = new Arena("pit", new BlockPos(1, 2, 3), 12).save();
        assertEquals("pit", tag.getString("Name"));
        assertEquals(1, tag.getInt("X"));
        assertEquals(2, tag.getInt("Y"));
        assertEquals(3, tag.getInt("Z"));
        assertEquals(12, tag.getInt("Radius"));
    }

    @Test
    void anEmptyTagLoadsAsAUsableArenaRatherThanThrowing() {
        Arena blank = Arena.load(new CompoundTag());
        assertEquals("", blank.name());
        assertEquals(BlockPos.ZERO, blank.centre());
        // A zero radius would put the ring inside the boss; the record clamps it up.
        assertEquals(Arena.MIN_RADIUS, blank.radius());
    }

    @Test
    void radiusIsClampedOnConstructionNotJustInTheCommand() {
        assertEquals(Arena.MIN_RADIUS, new Arena("a", BlockPos.ZERO, 0).radius());
        assertEquals(Arena.MIN_RADIUS, new Arena("a", BlockPos.ZERO, -900).radius());
        assertEquals(Arena.MAX_RADIUS, new Arena("a", BlockPos.ZERO, 9999).radius());
        assertEquals(20, new Arena("a", BlockPos.ZERO, 20).radius());
    }

    @Test
    void aClampedRadiusSurvivesTheRoundTrip() {
        Arena clamped = Arena.load(new Arena("a", BlockPos.ZERO, 9999).save());
        assertEquals(Arena.MAX_RADIUS, clamped.radius());
    }

    @Test
    void theDefaultsAreTheOnesTheCommandsAdvertise() {
        assertEquals("main", Arena.DEFAULT_NAME);
        assertEquals(20, Arena.DEFAULT_RADIUS);
        assertEquals(6, Arena.MIN_RADIUS);
        assertEquals(64, Arena.MAX_RADIUS);
    }

    @Test
    void arenaNamesAreCaseAndWhitespaceInsensitive() {
        assertEquals("main", ArenaSavedData.normalise("  Main "));
        assertEquals("main", ArenaSavedData.normalise("MAIN"));
        assertEquals("lava_pit", ArenaSavedData.normalise("Lava_Pit"));
    }

    @Test
    void normalisingIsIdempotent() {
        String once = ArenaSavedData.normalise(" Boss Arena ");
        assertEquals(once, ArenaSavedData.normalise(once));
    }

    @Test
    void arenasWithDifferentCentresAreDifferentArenas() {
        assertNotEquals(new Arena("main", new BlockPos(0, 0, 0), 20),
                new Arena("main", new BlockPos(0, 0, 1), 20));
    }
}
