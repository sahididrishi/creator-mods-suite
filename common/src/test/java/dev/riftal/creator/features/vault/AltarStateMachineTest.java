package dev.riftal.creator.features.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.vault.block.AltarState;
import dev.riftal.creator.features.vault.block.AltarStateMachine;
import dev.riftal.creator.features.vault.block.AltarStateMachine.Event;
import org.junit.jupiter.api.Test;

class AltarStateMachineTest {

    @Test
    void sealedPlusKeyCharges() {
        assertEquals(AltarState.CHARGING, AltarStateMachine.next(AltarState.SEALED, Event.KEY));
    }

    @Test
    void keyIsIgnoredUnlessSealed() {
        for (AltarState state : new AltarState[]{AltarState.CHARGING, AltarState.ACTIVE, AltarState.SPENT}) {
            assertEquals(state, AltarStateMachine.next(state, Event.KEY),
                    "a key offered to a " + state + " altar must change nothing");
            assertFalse(AltarStateMachine.acceptsKey(state));
        }
        assertTrue(AltarStateMachine.acceptsKey(AltarState.SEALED));
    }

    @Test
    void chargeCompletesIntoActive() {
        assertEquals(AltarState.ACTIVE,
                AltarStateMachine.next(AltarState.CHARGING, Event.CHARGE_COMPLETE));
        assertEquals(AltarState.SEALED,
                AltarStateMachine.next(AltarState.SEALED, Event.CHARGE_COMPLETE));
    }

    @Test
    void keeperDeathSpendsOnlyAnActiveAltar() {
        assertEquals(AltarState.SPENT, AltarStateMachine.next(AltarState.ACTIVE, Event.KEEPER_DEAD));
        assertEquals(AltarState.SEALED, AltarStateMachine.next(AltarState.SEALED, Event.KEEPER_DEAD));
        assertEquals(AltarState.CHARGING,
                AltarStateMachine.next(AltarState.CHARGING, Event.KEEPER_DEAD));
    }

    @Test
    void resetFromAnyStateIsSealed() {
        for (AltarState state : AltarState.values()) {
            assertEquals(AltarState.SEALED, AltarStateMachine.next(state, Event.RESET));
        }
    }

    @Test
    void chargeProgressIsZeroOutsideCharging() {
        for (AltarState state : AltarState.values()) {
            if (state != AltarState.CHARGING) {
                assertEquals(0.0F, AltarStateMachine.chargeProgress(state, 30), 1.0E-6F);
            }
        }
    }

    @Test
    void chargeProgressIsClampedAndLinear() {
        assertEquals(0.0F, AltarStateMachine.chargeProgress(AltarState.CHARGING, 0), 1.0E-6F);
        assertEquals(0.5F, AltarStateMachine.chargeProgress(AltarState.CHARGING,
                AltarStateMachine.CHARGE_TICKS / 2), 1.0E-6F);
        assertEquals(1.0F, AltarStateMachine.chargeProgress(AltarState.CHARGING,
                AltarStateMachine.CHARGE_TICKS), 1.0E-6F);
        assertEquals(1.0F, AltarStateMachine.chargeProgress(AltarState.CHARGING, 9999), 1.0E-6F);
    }

    @Test
    void timingsAreWholeSecondsAndPollsDivideTheMissingLimit() {
        assertEquals(0, AltarStateMachine.CHARGE_TICKS % 20,
                "the charge should be a whole number of seconds for the clip");
        assertEquals(0, AltarStateMachine.KEEPER_MISSING_LIMIT % AltarStateMachine.KEEPER_CHECK_INTERVAL,
                "the missing-keeper countdown must land exactly on a poll");
        assertTrue(AltarStateMachine.CHEST_SCAN_RADIUS > 0);
        assertTrue(AltarStateMachine.STATUS_BROADCAST_RANGE > AltarStateMachine.CHEST_SCAN_RADIUS);
    }

    @Test
    void onlyChargingAndActiveTick() {
        assertFalse(AltarState.SEALED.isTicking());
        assertTrue(AltarState.CHARGING.isTicking());
        assertTrue(AltarState.ACTIVE.isTicking());
        assertFalse(AltarState.SPENT.isTicking());
    }
}
