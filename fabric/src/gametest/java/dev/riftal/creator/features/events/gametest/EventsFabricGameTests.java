package dev.riftal.creator.features.events.gametest;

import dev.riftal.creator.features.events.EventsFeature;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric discovery stub for the {@code events} feature. Already listed in the {@code fabric-gametest}
 * entrypoint of {@code fabric/src/gametest/resources/fabric.mod.json} - do not edit that file, and
 * never add a sibling test class: Fabric only ever loads the nine classes named there, so a new
 * class is silently never run.
 *
 * <p>Fabric uses {@code template()} verbatim, so a template must be written as a full
 * {@code namespace:path}. Structures live in
 * {@code common/src/main/resources/data/creator_events/structure/}.
 *
 * <p><b>Every test that drives the director gets its own {@code batch}.</b> The director is a
 * process-wide singleton with exactly one active-event slot - that is the design
 * ({@code plans/06-event-director.md}: {@code start} while active replaces the running event) and
 * must not be softened. Tests inside one batch run <em>simultaneously</em> in adjacent arenas, so
 * two director tests sharing a batch would evict each other's event mid-assertion; batches, by
 * contrast, run one after another. {@code featureIsEnabled} and {@code allFiveEventsAreRegistered}
 * only read the registry and can stay in {@code defaultBatch}. The batch names mirror the NeoForge
 * stub one for one.
 */
public class EventsFabricGameTests implements FabricGameTest {

    private static final String EMPTY = EventsFeature.NAMESPACE + ":empty";

    @GameTest(template = EMPTY)
    public void featureIsEnabled(GameTestHelper helper) {
        EventsGameTests.featureIsEnabled(helper);
    }

    @GameTest(template = EMPTY)
    public void allFiveEventsAreRegistered(GameTestHelper helper) {
        EventsGameTests.allFiveEventsAreRegistered(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_events_bloodmoon")
    public void bloodMoonRaisesAndRestoresSpawnCap(GameTestHelper helper) {
        EventsGameTests.bloodMoonRaisesAndRestoresSpawnCap(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_events_replace")
    public void startingAnEventReplacesTheActiveOne(GameTestHelper helper) {
        EventsGameTests.startingAnEventReplacesTheActiveOne(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_events_timer")
    public void timerRetimesThePhaseAndSkipEndsTheEvent(GameTestHelper helper) {
        EventsGameTests.timerRetimesThePhaseAndSkipEndsTheEvent(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_events_meteor")
    public void meteorSkipsFromCountdownToFlight(GameTestHelper helper) {
        EventsGameTests.meteorSkipsFromCountdownToFlight(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_events_lucky")
    public void luckyEntityOutcomeSpawnsItsMob(GameTestHelper helper) {
        EventsGameTests.luckyEntityOutcomeSpawnsItsMob(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "creator_events_voidrise")
    public void voidPlaneClampsAndHandsOverToHold(GameTestHelper helper) {
        EventsGameTests.voidPlaneClampsAndHandsOverToHold(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 400, batch = "creator_events_meteor_impact")
    public void meteorImpactCreatesChest(GameTestHelper helper) {
        EventsGameTests.meteorImpactCreatesChest(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 400, batch = "creator_events_siege")
    public void siegeSpawnsFirstWave(GameTestHelper helper) {
        EventsGameTests.siegeSpawnsFirstWave(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "creator_events_dawn")
    public void bloodMoonSkipBringsDawn(GameTestHelper helper) {
        EventsGameTests.bloodMoonSkipBringsDawn(helper);
    }
}
