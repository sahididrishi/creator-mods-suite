package dev.riftal.creator.features.events.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge discovery stub for the {@code events} feature. Auto-scanned because of
 * {@link GameTestHolder}; {@code creator_events} is already listed in
 * {@code neoforge.enabledGameTestNamespaces}.
 *
 * <p>{@link PrefixGameTestTemplate}(false) keeps the template id at {@code creator_events:empty}
 * instead of {@code creator_events:Eventsneoforgegametests.empty}.
 *
 * <p>The {@code batch} names mirror the Fabric stub one for one, and for the same reason: the
 * director is a process-wide singleton with exactly one active-event slot (the design, see
 * {@code plans/06-event-director.md} - {@code start} while active replaces the running event).
 * Tests inside one batch run <em>simultaneously</em> in adjacent arenas, so two director tests
 * sharing a batch would evict each other's event mid-assertion. Every test that starts, stops,
 * skips or retimes an event therefore runs alone in its own batch; {@code featureIsEnabled} and
 * {@code allFiveEventsAreRegistered} only read the registry and can stay in {@code defaultBatch}.
 */
@GameTestHolder("creator_events")
@PrefixGameTestTemplate(false)
public class EventsNeoForgeGameTests {

    @GameTest(template = "empty")
    public void featureIsEnabled(GameTestHelper helper) {
        EventsGameTests.featureIsEnabled(helper);
    }

    @GameTest(template = "empty")
    public void allFiveEventsAreRegistered(GameTestHelper helper) {
        EventsGameTests.allFiveEventsAreRegistered(helper);
    }

    @GameTest(template = "empty", batch = "creator_events_bloodmoon")
    public void bloodMoonRaisesAndRestoresSpawnCap(GameTestHelper helper) {
        EventsGameTests.bloodMoonRaisesAndRestoresSpawnCap(helper);
    }

    @GameTest(template = "empty", batch = "creator_events_replace")
    public void startingAnEventReplacesTheActiveOne(GameTestHelper helper) {
        EventsGameTests.startingAnEventReplacesTheActiveOne(helper);
    }

    @GameTest(template = "empty", batch = "creator_events_timer")
    public void timerRetimesThePhaseAndSkipEndsTheEvent(GameTestHelper helper) {
        EventsGameTests.timerRetimesThePhaseAndSkipEndsTheEvent(helper);
    }

    @GameTest(template = "empty", batch = "creator_events_meteor")
    public void meteorSkipsFromCountdownToFlight(GameTestHelper helper) {
        EventsGameTests.meteorSkipsFromCountdownToFlight(helper);
    }

    @GameTest(template = "empty", batch = "creator_events_lucky")
    public void luckyEntityOutcomeSpawnsItsMob(GameTestHelper helper) {
        EventsGameTests.luckyEntityOutcomeSpawnsItsMob(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "creator_events_voidrise")
    public void voidPlaneClampsAndHandsOverToHold(GameTestHelper helper) {
        EventsGameTests.voidPlaneClampsAndHandsOverToHold(helper);
    }
}
