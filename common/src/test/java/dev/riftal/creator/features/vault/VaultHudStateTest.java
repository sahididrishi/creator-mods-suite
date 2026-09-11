package dev.riftal.creator.features.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.vault.block.AltarState;
import dev.riftal.creator.features.vault.block.AltarStateMachine;
import dev.riftal.creator.features.vault.net.VaultHudState;
import dev.riftal.creator.features.vault.net.VaultStatusPayload;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The client-side cache behind the vault HUD. It deliberately contains no
 * {@code net.minecraft.client.*} type - the S2C receiver is registered on both physical sides, so a
 * dedicated server has to be able to load it - which is exactly why it can be unit-tested here.
 */
class VaultHudStateTest {

    private static final BlockPos POS = new BlockPos(1, 2, 3);

    @BeforeEach
    void clearBefore() {
        VaultHudState.clear();
    }

    @AfterEach
    void clearAfter() {
        VaultHudState.clear();
    }

    @Test
    void nothingIsShownBeforeAPayloadArrives() {
        assertNull(VaultHudState.current());
        assertNull(VaultHudState.currentState());
    }

    @Test
    void theLastPayloadWins() {
        VaultHudState.accept(VaultStatusPayload.of(POS, AltarState.CHARGING, 10, 1, -1.0F, 0));
        VaultHudState.accept(VaultStatusPayload.of(POS, AltarState.ACTIVE, 60, 2, 0.75F, 4));

        VaultStatusPayload current = VaultHudState.current();
        assertNotNull(current);
        assertSame(AltarState.ACTIVE, current.state());
        assertSame(AltarState.ACTIVE, VaultHudState.currentState());
        assertEquals(60, current.chargeTicks());
        assertEquals(2, current.sealedChests());
        assertEquals(4, current.keysUsed());
    }

    @Test
    void clearingDropsTheCacheSoTheHudDisappears() {
        VaultHudState.accept(VaultStatusPayload.of(POS, AltarState.ACTIVE, 60, 1, 1.0F, 1));
        assertNotNull(VaultHudState.current());

        VaultHudState.clear();

        assertNull(VaultHudState.current());
        assertNull(VaultHudState.currentState());
    }

    @Test
    void twoAltarsInRangeDoNotOverwriteEachOther() {
        // The plan's failure-mode table lists "two altars within 16 blocks" as supported, and a
        // two-chest set arranges it trivially. With one global slot the two altars' 10-tick
        // broadcasts overwrote each other and the readout flickered between them.
        BlockPos near = new BlockPos(0, 64, 0);
        BlockPos far = new BlockPos(20, 64, 0);
        VaultHudState.accept(VaultStatusPayload.of(near, AltarState.CHARGING, 30, 1, -1.0F, 0));
        VaultHudState.accept(VaultStatusPayload.of(far, AltarState.SPENT, 0, 2, -1.0F, 0));

        assertEquals(2, VaultHudState.tracked(), "both altars should be tracked");

        VaultStatusPayload fromNear = VaultHudState.current(new BlockPos(2, 64, 0));
        assertNotNull(fromNear);
        assertEquals(near, fromNear.altarPos());
        assertSame(AltarState.CHARGING, fromNear.state());

        VaultStatusPayload fromFar = VaultHudState.current(new BlockPos(18, 64, 0));
        assertNotNull(fromFar);
        assertEquals(far, fromFar.altarPos());
        assertSame(AltarState.SPENT, fromFar.state());
    }

    @Test
    void theStaleWindowIsShortEnoughToHideTheHudButLongerThanTheBroadcastPeriod() {
        // The altar broadcasts every STATUS_BROADCAST_INTERVAL ticks; if the stale window were
        // shorter than that the HUD would flicker between every packet.
        assertTrue(VaultHudState.STALE_AFTER_TICKS > AltarStateMachine.STATUS_BROADCAST_INTERVAL,
                "the HUD would flicker between broadcasts");
        // ...and short enough that the readout is gone within a few seconds of walking away.
        assertTrue(VaultHudState.STALE_AFTER_TICKS <= 100,
                "the readout must clear itself within a few seconds of walking away");
    }
}
