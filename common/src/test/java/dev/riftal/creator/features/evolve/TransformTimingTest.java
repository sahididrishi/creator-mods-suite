package dev.riftal.creator.features.evolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.evolve.client.ClientEvolutionCache;
import dev.riftal.creator.features.evolve.data.EvolutionData;
import dev.riftal.creator.features.evolve.net.XpPopupPayload;
import dev.riftal.creator.features.evolve.progression.Transformation;
import dev.riftal.creator.features.evolve.stage.Stages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/**
 * Transformation timing on both sides of the wire: how long the server locks the player for, and
 * the wall-clock curves the HUD draws from ({@code ClientEvolutionCache} is deliberately free of
 * {@code net.minecraft.client} types, so its arithmetic is unit-testable).
 */
class TransformTimingTest {

    private static final UUID PLAYER = UUID.fromString("8a1b2c3d-4e5f-4061-8273-84950a1b2c3d");

    @BeforeEach
    @AfterEach
    void wipeTheCache() {
        ClientEvolutionCache.clear();
    }

    @Test
    void apexTakesLongerThanEveryOtherStage() {
        for (int stage = Stages.MIN; stage < Stages.MAX; stage++) {
            assertEquals(Transformation.TICKS, Transformation.durationFor(stage),
                    "stage " + stage + " should use the normal sequence length");
        }
        assertEquals(Transformation.APEX_TICKS, Transformation.durationFor(Stages.MAX));
        assertTrue(Transformation.APEX_TICKS > Transformation.TICKS,
                "the Apex sequence is the money shot and has to be the long one");
    }

    @Test
    void outOfRangeStagesStillGetASaneDuration() {
        assertEquals(Transformation.TICKS, Transformation.durationFor(0));
        assertEquals(Transformation.APEX_TICKS, Transformation.durationFor(99));
    }

    @Test
    void transformProgressRunsZeroToOneOverTheSequence() {
        long start = 1_000_000L;
        ClientEvolutionCache.Transform transform =
                new ClientEvolutionCache.Transform(start, 40, 3);

        assertEquals(start + 2_000L, transform.endMillis(), "40 ticks is two seconds");
        assertEquals(0.0F, transform.progress(start), 1.0E-6F);
        assertEquals(0.5F, transform.progress(start + 1_000L), 1.0E-6F);
        assertEquals(1.0F, transform.progress(start + 2_000L), 1.0E-6F);
        assertEquals(0.0F, transform.progress(start - 5_000L), 1.0E-6F, "clamped before the start");
        assertEquals(1.0F, transform.progress(start + 60_000L), 1.0E-6F, "clamped after the end");
    }

    @Test
    void theWhiteFlashOnlyCoversTheEndOfTheSequence() {
        long start = 5_000L;
        ClientEvolutionCache.Transform transform =
                new ClientEvolutionCache.Transform(start, 60, Stages.MAX);
        long end = transform.endMillis();

        assertEquals(0.0F, transform.flash(start), 1.0E-6F, "no flash at the top of the sequence");
        assertEquals(0.0F, transform.flash(end - ClientEvolutionCache.FLASH_MILLIS), 1.0E-6F);
        assertTrue(transform.flash(end - ClientEvolutionCache.FLASH_MILLIS / 2) > 0.4F,
                "the flash should be ramping up half a flash-length out");
        assertEquals(1.0F, transform.flash(end), 1.0E-6F, "peak white exactly as the body lands");
        assertTrue(transform.flash(end + ClientEvolutionCache.FLASH_MILLIS / 4) < 1.0F,
                "and fade back out afterwards");
        assertEquals(0.0F, transform.flash(end + ClientEvolutionCache.FLASH_MILLIS), 1.0E-6F);
    }

    @Test
    void popupsAgeOutAndAreDroppedWhenTheyExpire() {
        ClientEvolutionCache.addPopup(15, XpPopupPayload.SOURCE_KILL);
        List<ClientEvolutionCache.Popup> live = ClientEvolutionCache.popups();
        assertEquals(1, live.size());

        ClientEvolutionCache.Popup popup = live.get(0);
        assertEquals(15, popup.amount());
        assertEquals(0.0F, popup.age(popup.shownAtMillis()), 1.0E-6F);
        assertEquals(0.5F, popup.age(popup.shownAtMillis() + ClientEvolutionCache.POPUP_MILLIS / 2),
                1.0E-2F);
        assertEquals(1.0F, popup.age(popup.shownAtMillis() + ClientEvolutionCache.POPUP_MILLIS * 4),
                1.0E-6F);
    }

    @Test
    void onlyASmallNumberOfPopupsIsKept() {
        for (int i = 0; i < 25; i++) {
            ClientEvolutionCache.addPopup(i, XpPopupPayload.SOURCE_KILL);
        }
        List<ClientEvolutionCache.Popup> live = ClientEvolutionCache.popups();
        assertTrue(live.size() <= 6, "a kill streak must not fill the screen: " + live.size());
        assertEquals(24, live.get(live.size() - 1).amount(), "the newest pop-up survives");
    }

    @Test
    void everyXpSourceHasItsOwnColourAndIsOpaque() {
        int kill = ClientEvolutionCache.popupColour(XpPopupPayload.SOURCE_KILL);
        int food = ClientEvolutionCache.popupColour(XpPopupPayload.SOURCE_FOOD);
        int command = ClientEvolutionCache.popupColour(XpPopupPayload.SOURCE_COMMAND);

        assertTrue(kill != food && food != command && kill != command,
                "the three XP sources should be told apart by colour");
        for (int colour : new int[] {kill, food, command}) {
            assertEquals(0xFF, (colour >>> 24) & 0xFF, "pop-up colours must be fully opaque");
        }
    }

    @Test
    void aSyncedStateIsRememberedPerPlayerAndClearedOnALevelChange() {
        assertFalse(ClientEvolutionCache.knows(PLAYER));
        assertEquals(EvolutionData.INITIAL, ClientEvolutionCache.state(PLAYER));

        ClientEvolutionCache.put(PLAYER, 5, 1_800, false, EvolutionData.MODEL_AUTO);
        assertTrue(ClientEvolutionCache.knows(PLAYER));
        assertEquals(5, ClientEvolutionCache.state(PLAYER).stage());
        assertTrue(ClientEvolutionCache.state(PLAYER).usesBeastModel());

        ClientEvolutionCache.onLevel(new Object());
        assertFalse(ClientEvolutionCache.knows(PLAYER), "a new level must wipe the old world's stages");
    }

    @Test
    void aFinishedSyncDropsTheRunningTransform() {
        ClientEvolutionCache.startTransform(PLAYER, 40, 2);
        assertNotNull(ClientEvolutionCache.transform(PLAYER));

        ClientEvolutionCache.put(PLAYER, 2, 120, false, EvolutionData.MODEL_AUTO);
        assertNull(ClientEvolutionCache.transform(PLAYER),
                "a sync that says 'not transforming' ends the screen effect");
    }
}
