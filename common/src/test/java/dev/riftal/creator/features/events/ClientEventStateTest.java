package dev.riftal.creator.features.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.events.client.ClientEventState;
import dev.riftal.creator.features.events.net.EventStatePayload;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The client mirror. It is a process-wide static with no Minecraft client types in it, so all of it
 * is reachable from a plain JUnit run - which matters, because the two bugs this covers (a tint that
 * followed the player into the next world, and a fade whose speed depended on framerate) were both
 * invisible until someone recorded with them.
 */
class ClientEventStateTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    private static final EventStatePayload BLOOD_MOON = new EventStatePayload(
            "bloodmoon", "night", 1, 20, -1, 0.5F,
            0.6F, 0.0F, 0.0F, 0.85F,
            Double.NaN, 0, 0, 0, true,
            OVERWORLD, "creator_events:bloodmoon.drone");

    @BeforeEach
    @AfterEach
    void clear() {
        ClientEventState.reset();
    }

    @Test
    void resetClearsTheMirrorSoItCannotFollowYouIntoTheNextWorld() {
        ClientEventState.accept(BLOOD_MOON);
        for (int tick = 0; tick < 200; tick++) {
            ClientEventState.clientTick(64.0D, OVERWORLD);
        }
        assertTrue(ClientEventState.hudVisible(), "the HUD line is up during a blood moon");
        assertTrue(ClientEventState.shownStrength() > 0.5F, "the sky is red by now");

        // Quitting to the title: this is the call the client-side disconnect mixin makes.
        ClientEventState.reset();

        assertSame(EventStatePayload.IDLE, ClientEventState.state());
        assertFalse(ClientEventState.hudVisible(), "no HUD line in a world with no event");
        assertEquals(0.0F, ClientEventState.shownStrength(), 1.0E-6F, "and no red sky either");
        assertEquals("", ClientEventState.ambientLoop(), "and no drone");
        Vec3 vanilla = new Vec3(0.4D, 0.6D, 0.9D);
        assertSame(vanilla, ClientEventState.tintColour(vanilla),
                "an idle mirror returns the vanilla colour untouched");
    }

    @Test
    void theFadeIsDrivenByTicksNotByFrames() {
        ClientEventState.accept(BLOOD_MOON);
        for (int tick = 0; tick < 400; tick++) {
            ClientEventState.clientTick(64.0D, OVERWORLD);
        }
        float settled = ClientEventState.shownStrength();
        assertEquals(0.85F, settled, 0.01F, "the follow lerp converges on what the server sent");

        // Rendering does not advance it: a 240 fps capture rig and a 20 fps one must fade at the
        // same wall-clock speed.
        Vec3 vanilla = new Vec3(0.4D, 0.6D, 0.9D);
        for (int frame = 0; frame < 100; frame++) {
            ClientEventState.tintColour(vanilla);
        }
        assertEquals(settled, ClientEventState.shownStrength(), 1.0E-6F);

        ClientEventState.accept(EventStatePayload.IDLE);
        for (int tick = 0; tick < 400; tick++) {
            ClientEventState.clientTick(64.0D, OVERWORLD);
        }
        assertEquals(0.0F, ClientEventState.shownStrength(), 1.0E-6F, "and it fades back out");
    }

    @Test
    void anotherDimensionGetsNeitherTheHudLineNorTheTint() {
        ClientEventState.accept(BLOOD_MOON);
        for (int tick = 0; tick < 200; tick++) {
            ClientEventState.clientTick(64.0D, NETHER);
        }
        assertFalse(ClientEventState.hudVisible(),
                "an Overworld blood moon must not caption the Nether");
        assertEquals(0.0F, ClientEventState.shownStrength(), 1.0E-6F);
        assertEquals("", ClientEventState.ambientLoop());
    }

    @Test
    void theVoidTintScalesWithHowCloseTheCameraIsToThePlane() {
        EventStatePayload voidRise = new EventStatePayload(
                "voidrise", "rise", 0, 10, -1, 0.1F,
                0.0F, 0.0F, 0.0F, 0.30F,
                0.0D, 0, 0, 0, true,
                OVERWORLD, "");
        ClientEventState.accept(voidRise);

        // 100 blocks above the plane: the plane is not even visible, so nothing is tinted.
        ClientEventState.clientTick(100.0D, OVERWORLD);
        assertEquals(0.0F, ClientEventState.targetStrength(), 1.0E-6F);

        // Standing in it: the full 0.3 the server sent.
        ClientEventState.clientTick(0.0D, OVERWORLD);
        assertEquals(0.30F, ClientEventState.targetStrength(), 1.0E-6F);

        // Half way through the falloff window.
        ClientEventState.clientTick(24.0D, OVERWORLD);
        assertEquals(0.15F, ClientEventState.targetStrength(), 1.0E-3F);
    }

    @Test
    void aRunningEventNamesTheLoopTheClientShouldPlay() {
        ClientEventState.accept(BLOOD_MOON);
        ClientEventState.clientTick(64.0D, OVERWORLD);
        assertEquals("creator_events:bloodmoon.drone", ClientEventState.ambientLoop());

        ClientEventState.accept(EventStatePayload.IDLE);
        ClientEventState.clientTick(64.0D, OVERWORLD);
        assertEquals("", ClientEventState.ambientLoop(), "stopping the event stops the loop");
    }
}
