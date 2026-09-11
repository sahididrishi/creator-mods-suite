package dev.riftal.creator.features.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.vault.block.AltarState;
import dev.riftal.creator.features.vault.net.VaultStatusPayload;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * The HUD payload is the only place the altar's state crosses the wire as a raw number, so the
 * enum/ordinal conversion is worth pinning down: an off-by-one here would show "SPENT" on a
 * charging altar in the middle of a take, and nothing would log.
 *
 * <p>Pure record plus codec constants - no registry is touched, so this runs without the game.
 */
class VaultStatusPayloadTest {

    private static final BlockPos POS = new BlockPos(12, -30, -7);

    @Test
    void everyStateSurvivesTheOrdinalRoundTrip() {
        for (AltarState state : AltarState.values()) {
            VaultStatusPayload payload = VaultStatusPayload.of(POS, state, 17, 2, 0.5F, 3);
            assertSame(state, payload.state(),
                    "state " + state + " came back as " + payload.state());
            assertEquals((byte) state.ordinal(), payload.stateId());
        }
    }

    @Test
    void theOtherFieldsArePassedThroughUnchanged() {
        VaultStatusPayload payload =
                VaultStatusPayload.of(POS, AltarState.CHARGING, 42, 5, 0.25F, 9);
        assertEquals(POS, payload.altarPos());
        assertEquals(42, payload.chargeTicks());
        assertEquals(5, payload.sealedChests());
        assertEquals(0.25F, payload.keeperHealth(), 1.0E-6F);
        assertEquals(9, payload.keysUsed());
    }

    @Test
    void anOutOfRangeOrdinalDecodesToSealedRatherThanThrowing() {
        // A desynced or hostile client must not be able to throw inside the HUD render loop.
        assertSame(AltarState.SEALED,
                new VaultStatusPayload(POS, (byte) 99, 0, 0, -1.0F, 0).state());
        assertSame(AltarState.SEALED,
                new VaultStatusPayload(POS, (byte) -1, 0, 0, -1.0F, 0).state());
    }

    @Test
    void noKeeperIsCarriedAsANegativeHealth() {
        // The HUD only draws the Keeper bar for keeperHealth() >= 0, so "no Keeper" has to be
        // representable; 0.0F would mean "alive with an empty bar".
        VaultStatusPayload payload =
                VaultStatusPayload.of(POS, AltarState.ACTIVE, 60, 1, -1.0F, 0);
        assertTrue(payload.keeperHealth() < 0.0F, "no Keeper must be representable");
    }

    @Test
    void thePayloadIdLivesInThisFeaturesNamespace() {
        assertNotNull(VaultStatusPayload.TYPE);
        assertEquals(VaultFeature.NAMESPACE, VaultStatusPayload.TYPE.id().getNamespace());
        assertEquals("vault_status", VaultStatusPayload.TYPE.id().getPath());
        assertNotNull(VaultStatusPayload.CODEC);
    }
}
