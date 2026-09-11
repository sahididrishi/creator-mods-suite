package dev.riftal.creator.features.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.toolkit.cheat.CheatFlags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class CheatFlagsTest {

    @Test
    void togglesProduceNewValues() {
        CheatFlags none = CheatFlags.NONE;
        assertFalse(none.any());
        CheatFlags god = none.withGod(true);
        assertTrue(god.god());
        assertFalse(god.fly());
        assertFalse(none.god(), "the original value must not be mutated");
        assertTrue(god.withFly(true).any());
    }

    @Test
    void roundTripsThroughItsCodec() {
        CheatFlags original = new CheatFlags(true, false);
        Tag encoded = CheatFlags.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        CheatFlags decoded = CheatFlags.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(original, decoded);
    }

    @Test
    void defaultsFillInMissingFields() {
        CheatFlags decoded = CheatFlags.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
        assertEquals(CheatFlags.NONE, decoded);
    }
}
