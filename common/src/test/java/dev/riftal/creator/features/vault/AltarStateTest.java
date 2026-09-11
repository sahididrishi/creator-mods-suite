package dev.riftal.creator.features.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.vault.block.AltarState;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AltarStateTest {

    @Test
    void serializedNamesRoundTrip() {
        for (AltarState state : AltarState.values()) {
            assertNotNull(state.getSerializedName());
            assertEquals(state, AltarState.byName(state.getSerializedName()));
        }
    }

    @Test
    void unknownNamesFallBackToSealed() {
        assertEquals(AltarState.SEALED, AltarState.byName("nonsense"));
        assertEquals(AltarState.SEALED, AltarState.byName(null));
        assertEquals(AltarState.SEALED, AltarState.byName(""));
    }

    @Test
    void serializedNamesAreLowercaseAndUnique() {
        Set<String> seen = new HashSet<>();
        for (AltarState state : AltarState.values()) {
            String name = state.getSerializedName();
            assertEquals(name.toLowerCase(Locale.ROOT), name);
            assertTrue(seen.add(name), "duplicate serialized name " + name);
        }
    }
}
