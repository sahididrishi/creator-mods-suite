package dev.riftal.creator.features.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.events.api.EventContext;
import dev.riftal.creator.features.events.api.EventPhase;
import dev.riftal.creator.features.events.api.StopReason;
import dev.riftal.creator.features.events.api.WorldEvent;
import dev.riftal.creator.features.events.events.BloodMoonEvent;
import dev.riftal.creator.features.events.events.LuckyRainEvent;
import dev.riftal.creator.features.events.events.MeteorEvent;
import dev.riftal.creator.features.events.events.SiegeEvent;
import dev.riftal.creator.features.events.events.VoidRiseEvent;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The phase contract every {@link WorldEvent} is driven by: durations, open phases and the
 * progress bar the HUD reads. Nothing here needs a level - the manager passes the context straight
 * through to these methods and the defaults never touch it.
 */
class PhaseMachineTest {

    /** A two-phase event using nothing but the interface defaults. */
    private static final class TimedEvent implements WorldEvent {

        @Override
        public String id() {
            return "timed";
        }

        @Override
        public List<EventPhase> phases() {
            return List.of(EventPhase.ticks("a", 10), EventPhase.ticks("b", 5));
        }

        @Override
        public void onStart(EventContext ctx, boolean resumed) {
        }

        @Override
        public void onStop(EventContext ctx, StopReason reason) {
        }
    }

    /** An event whose only phase ends when a flag flips, like bloodmoon's "until dawn". */
    private static final class OpenEvent implements WorldEvent {

        boolean dawn;

        @Override
        public String id() {
            return "open";
        }

        @Override
        public List<EventPhase> phases() {
            return List.of(EventPhase.open("night"));
        }

        @Override
        public void onStart(EventContext ctx, boolean resumed) {
        }

        @Override
        public boolean isPhaseComplete(EventContext ctx, EventPhase phase, int phaseTick) {
            return dawn;
        }

        @Override
        public void onStop(EventContext ctx, StopReason reason) {
        }
    }

    @Test
    void phaseFactoriesClampAndConvert() {
        assertEquals(10, EventPhase.ticks("a", 10).durationTicks());
        assertEquals(1, EventPhase.ticks("a", 0).durationTicks(), "a timed phase is never zero-length");
        assertEquals(1, EventPhase.ticks("a", -40).durationTicks());
        assertEquals(60, EventPhase.seconds("a", 3).durationTicks());
        assertFalse(EventPhase.ticks("a", 10).isOpen());
        assertTrue(EventPhase.open("night").isOpen());
        assertEquals(-1, EventPhase.open("night").durationTicks());
    }

    @Test
    void timedPhaseCompletesExactlyOnItsDuration() {
        TimedEvent event = new TimedEvent();
        EventPhase a = event.phases().get(0);
        for (int tick = 1; tick < 10; tick++) {
            assertFalse(event.isPhaseComplete(null, a, tick), "phase a ended early at tick " + tick);
        }
        assertTrue(event.isPhaseComplete(null, a, 10), "phase a should end on its tenth tick");
        assertTrue(event.isPhaseComplete(null, a, 11));

        EventPhase b = event.phases().get(1);
        assertFalse(event.isPhaseComplete(null, b, 4));
        assertTrue(event.isPhaseComplete(null, b, 5));
    }

    @Test
    void openPhaseWaitsForItsCondition() {
        OpenEvent event = new OpenEvent();
        EventPhase night = event.phases().get(0);
        for (int tick = 1; tick < 5000; tick++) {
            assertFalse(event.isPhaseComplete(null, night, tick), "an open phase never ends on time");
        }
        event.dawn = true;
        assertTrue(event.isPhaseComplete(null, night, 1));
    }

    @Test
    void progressIsClampedAndZeroForOpenPhases() {
        TimedEvent event = new TimedEvent();
        EventPhase a = event.phases().get(0);
        assertEquals(0.0F, event.progress(null, a, 0), 1.0E-6F);
        assertEquals(0.5F, event.progress(null, a, 5), 1.0E-6F);
        assertEquals(1.0F, event.progress(null, a, 10), 1.0E-6F);
        assertEquals(1.0F, event.progress(null, a, 99), 1.0E-6F, "progress never exceeds 1");
        assertEquals(0.0F, event.progress(null, a, -3), 1.0E-6F, "progress never drops below 0");
        assertEquals(0.0F, event.progress(null, EventPhase.open("night"), 400), 1.0E-6F);
    }

    @Test
    void everyShippedEventDeclaresItsPlannedPhases() {
        assertEquals(List.of("rise", "night", "fade"), ids(new BloodMoonEvent()));
        assertEquals(List.of("countdown", "flight", "impact", "aftermath"), ids(new MeteorEvent()));
        assertEquals(List.of("prepare", "waves", "victory"), ids(new SiegeEvent()));
        assertEquals(List.of("rain"), ids(new LuckyRainEvent()));
        assertEquals(List.of("rise", "hold"), ids(new VoidRiseEvent()));
    }

    @Test
    void openPhasesAreExactlyTheOnesThatWaitOnTheWorld() {
        assertTrue(new BloodMoonEvent().phases().get(1).isOpen(), "bloodmoon's night ends at dawn");
        assertTrue(new SiegeEvent().phases().get(1).isOpen(), "siege's waves end when they are cleared");
        assertTrue(new VoidRiseEvent().phases().get(0).isOpen(), "the void rises until it is capped");
        for (EventPhase phase : new MeteorEvent().phases()) {
            assertFalse(phase.isOpen(), "every meteor phase is on a clock: " + phase.id());
        }
    }

    @Test
    void bloodMoonCountdownConstantsMatchThePlan() {
        assertEquals(40, BloodMoonEvent.RAMP_TICKS);
        assertEquals(2.0F, BloodMoonEvent.SPAWN_MULTIPLIER);
        assertEquals(6000, BloodMoonEvent.MAX_NIGHT_TICKS,
                "the doDaylightCycle=false fallback is five in-game minutes");
        assertEquals(BloodMoonEvent.RAMP_TICKS, new BloodMoonEvent().phases().get(0).durationTicks());
    }

    @Test
    void voidRiseTimerIsConsumedByTheEventItself() {
        VoidRiseEvent event = new VoidRiseEvent();
        // The manager offers the timer to the event first; voidrise reinterprets it as "reach the
        // ceiling in this long" and returns true so the manager does not retime the phase instead.
        assertTrue(event.onTimer(null, 60), "voidrise must consume /event timer itself");
        assertFalse(new MeteorEvent().onTimer(null, 60), "meteor leaves the timer to the manager");
        assertFalse(new BloodMoonEvent().onTimer(null, 60));
        assertFalse(new SiegeEvent().onTimer(null, 60));
        assertFalse(new LuckyRainEvent().onTimer(null, 60));
    }

    @Test
    void meteorTimingsAddUpToAFlightThatLands() {
        MeteorEvent meteor = new MeteorEvent();
        assertEquals(100, MeteorEvent.COUNTDOWN_TICKS, "five seconds of title cards");
        assertEquals(MeteorEvent.FLIGHT_TICKS, meteor.phases().get(1).durationTicks());
        MeteorEvent.MeteorSpawn spawn =
                MeteorEvent.MeteorSpawn.of(MeteorEvent.SPAWN_OFFSET, Vec3.ZERO);
        assertTrue(spawn.travelTicks() < MeteorEvent.FLIGHT_TICKS,
                "the boulder must arrive before the flight phase runs out, took "
                        + spawn.travelTicks() + " of " + MeteorEvent.FLIGHT_TICKS);
    }

    private static List<String> ids(WorldEvent event) {
        List<String> out = new ArrayList<>();
        for (EventPhase phase : event.phases()) {
            out.add(phase.id());
        }
        return out;
    }
}
