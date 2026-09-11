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

    @GameTest(template = "empty")
    public void bloodMoonRaisesAndRestoresSpawnCap(GameTestHelper helper) {
        EventsGameTests.bloodMoonRaisesAndRestoresSpawnCap(helper);
    }

    @GameTest(template = "empty")
    public void startingAnEventReplacesTheActiveOne(GameTestHelper helper) {
        EventsGameTests.startingAnEventReplacesTheActiveOne(helper);
    }

    @GameTest(template = "empty")
    public void timerRetimesThePhaseAndSkipEndsTheEvent(GameTestHelper helper) {
        EventsGameTests.timerRetimesThePhaseAndSkipEndsTheEvent(helper);
    }

    @GameTest(template = "empty")
    public void meteorSkipsFromCountdownToFlight(GameTestHelper helper) {
        EventsGameTests.meteorSkipsFromCountdownToFlight(helper);
    }

    @GameTest(template = "empty")
    public void luckyEntityOutcomeSpawnsItsMob(GameTestHelper helper) {
        EventsGameTests.luckyEntityOutcomeSpawnsItsMob(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public void voidPlaneClampsAndHandsOverToHold(GameTestHelper helper) {
        EventsGameTests.voidPlaneClampsAndHandsOverToHold(helper);
    }
}
