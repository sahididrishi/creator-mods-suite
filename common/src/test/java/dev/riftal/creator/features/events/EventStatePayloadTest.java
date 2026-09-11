package dev.riftal.creator.features.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.events.net.EventStatePayload;
import org.junit.jupiter.api.Test;

/**
 * The hand-written seventeen-field state record. The stream codec itself needs a registry-aware
 * buffer and therefore a booted game, so it belongs in a GameTest; what is testable here is the
 * record contract the HUD and the sky mixin read, and the copy the manager uses for its idle
 * broadcast - a hand-written copy constructor with seventeen arguments is exactly the kind of thing
 * that silently drops a field.
 */
class EventStatePayloadTest {

    private static final EventStatePayload SIEGE = new EventStatePayload(
            "siege", "waves", 1, 37, -1, 0.42F,
            0.6F, 0.1F, 0.2F, 0.85F,
            12.5D, 3, 5, 17, true,
            "minecraft:overworld", "creator_events:bloodmoon.drone");

    @Test
    void idleCarriesNothingAndReportsItself() {
        assertFalse(EventStatePayload.IDLE.active(), "an idle payload must clear the client");
        assertEquals("", EventStatePayload.IDLE.eventId());
        assertEquals("", EventStatePayload.IDLE.phaseId());
        assertEquals(-1, EventStatePayload.IDLE.phaseDuration(), "no phase, no duration");
        assertEquals(0.0F, EventStatePayload.IDLE.tintStrength(), 1.0E-6F,
                "an idle director never tints the sky");
        assertEquals(0.0F, EventStatePayload.IDLE.progress(), 1.0E-6F);
        assertTrue(Double.isNaN(EventStatePayload.IDLE.voidY()), "no kill plane while idle");
        assertEquals(0, EventStatePayload.IDLE.wave());
        assertEquals(0, EventStatePayload.IDLE.waveTotal());
        assertEquals(0, EventStatePayload.IDLE.alive());
        assertEquals("", EventStatePayload.IDLE.dimension(), "an idle director is nowhere");
        assertEquals("", EventStatePayload.IDLE.ambientLoop(), "an idle director loops nothing");
    }

    @Test
    void stateOnlyAppliesInItsOwnDimension() {
        assertTrue(SIEGE.appliesTo("minecraft:overworld"));
        assertFalse(SIEGE.appliesTo("minecraft:the_nether"),
                "a player in the Nether must not get an Overworld siege's HUD line");
        assertFalse(SIEGE.appliesTo(""), "an unknown viewer dimension matches nothing");
        assertTrue(EventStatePayload.IDLE.appliesTo("minecraft:the_end"),
                "idle carries no dimension and clears the client everywhere");
    }

    @Test
    void anEventIdMakesThePayloadActive() {
        assertTrue(SIEGE.active());
        assertEquals("siege", SIEGE.eventId());
        assertEquals("waves", SIEGE.phaseId());
    }

    @Test
    void withHudChangesTheFlagAndNothingElse() {
        EventStatePayload hidden = SIEGE.withHud(false);
        assertFalse(hidden.hud());
        assertTrue(SIEGE.hud(), "withHud must not mutate the original");
        assertEquals(SIEGE, hidden.withHud(true), "flipping back restores every other field");

        assertEquals(SIEGE.eventId(), hidden.eventId());
        assertEquals(SIEGE.phaseId(), hidden.phaseId());
        assertEquals(SIEGE.phaseIndex(), hidden.phaseIndex());
        assertEquals(SIEGE.phaseTick(), hidden.phaseTick());
        assertEquals(SIEGE.phaseDuration(), hidden.phaseDuration());
        assertEquals(SIEGE.progress(), hidden.progress(), 1.0E-6F);
        assertEquals(SIEGE.tintR(), hidden.tintR(), 1.0E-6F);
        assertEquals(SIEGE.tintG(), hidden.tintG(), 1.0E-6F);
        assertEquals(SIEGE.tintB(), hidden.tintB(), 1.0E-6F);
        assertEquals(SIEGE.tintStrength(), hidden.tintStrength(), 1.0E-6F);
        assertEquals(SIEGE.voidY(), hidden.voidY(), 1.0E-9D);
        assertEquals(SIEGE.wave(), hidden.wave());
        assertEquals(SIEGE.waveTotal(), hidden.waveTotal());
        assertEquals(SIEGE.alive(), hidden.alive());
        assertEquals(SIEGE.dimension(), hidden.dimension());
        assertEquals(SIEGE.ambientLoop(), hidden.ambientLoop());
    }

    @Test
    void theIdlePayloadKeepsItsHudFlagWhenHidden() {
        // EventManager#stop broadcasts IDLE.withHud(hudVisible) - the flag has to survive that even
        // though every other field is empty, or /event hud false is forgotten on every stop.
        assertFalse(EventStatePayload.IDLE.withHud(false).hud());
        assertFalse(EventStatePayload.IDLE.withHud(false).active());
        assertTrue(EventStatePayload.IDLE.withHud(true).hud());
    }

    @Test
    void payloadAdvertisesItsRegisteredType() {
        assertNotNull(EventStatePayload.TYPE);
        assertEquals("creator_events", EventStatePayload.TYPE.id().getNamespace());
        assertEquals("event_state", EventStatePayload.TYPE.id().getPath());
        assertSame(EventStatePayload.TYPE, SIEGE.type(), "every instance reports the same type");
        assertNotNull(EventStatePayload.CODEC);
    }
}
