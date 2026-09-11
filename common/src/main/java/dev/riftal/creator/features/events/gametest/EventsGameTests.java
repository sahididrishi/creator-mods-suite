package dev.riftal.creator.features.events.gametest;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.events.EventManager;
import dev.riftal.creator.features.events.EventsFeature;
import dev.riftal.creator.features.events.api.EventPhase;
import dev.riftal.creator.features.events.api.EventRegistry;
import dev.riftal.creator.features.events.api.StopReason;
import dev.riftal.creator.features.events.api.WorldEvent;
import dev.riftal.creator.features.events.hooks.EventHooks;
import dev.riftal.creator.features.events.lucky.LuckyOutcome;
import dev.riftal.creator.features.events.lucky.LuckyOutcomeRunner;
import dev.riftal.creator.features.events.util.EventOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;

/**
 * GameTest bodies for the {@code events} feature. Vanilla API only, so both loaders can call them.
 *
 * <p>Add a {@code public static void name(GameTestHelper helper)} here, then one annotated stub in
 * {@code fabric/src/gametest/java/.../EventsFabricGameTests.java} and one in
 * {@code neoforge/src/main/java/.../EventsNeoForgeGameTests.java}.
 */
public final class EventsGameTests {

    /** Smoke test: the feature survived the config filter and is live in this session. */
    public static void featureIsEnabled(GameTestHelper helper) {
        helper.assertTrue(CreatorMods.isEnabled(EventsFeature.ID),
                "feature '" + EventsFeature.ID + "' should be enabled in the test session");
        helper.succeed();
    }

    /** Every event in the plan is registered and reachable by {@code /event start}. */
    public static void allFiveEventsAreRegistered(GameTestHelper helper) {
        for (String id : new String[] {"bloodmoon", "meteor", "siege", "luckyrain", "voidrise"}) {
            helper.assertTrue(EventRegistry.contains(id), "event '" + id + "' should be registered");
            helper.assertTrue(EventRegistry.create(id) != null, "event '" + id + "' should build");
        }
        helper.succeed();
    }

    /**
     * A blood moon raises the monster spawn cap while it runs and puts it back when it stops - the
     * one piece of global state this feature mutates, and the one that must never leak.
     */
    public static void bloodMoonRaisesAndRestoresSpawnCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));

        helper.assertTrue(EventHooks.spawnCapMultiplier(MobCategory.MONSTER) == 1.0F,
                "spawn cap should start at 1.0");
        boolean started = EventManager.start("bloodmoon", level.getServer(), level, origin, null,
                EventOptions.empty());
        helper.assertTrue(started, "bloodmoon should start");
        helper.assertValueEqual(EventManager.activeId(), "bloodmoon", "active event id");
        helper.assertTrue(EventHooks.spawnCapMultiplier(MobCategory.MONSTER) == 2.0F,
                "blood moon should double the monster cap");

        helper.assertTrue(EventManager.stop(StopReason.STOPPED), "bloodmoon should stop");
        helper.assertTrue(EventHooks.spawnCapMultiplier(MobCategory.MONSTER) == 1.0F,
                "stopping must put the monster cap back");
        helper.assertValueEqual(EventManager.activeId(), "", "no event should be active");
        helper.succeed();
    }

    /** Starting a second event replaces the first instead of stacking with it. */
    public static void startingAnEventReplacesTheActiveOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
        EventManager.start("bloodmoon", level.getServer(), level, origin, null, EventOptions.empty());
        EventManager.start("voidrise", level.getServer(), level, origin, null,
                EventOptions.parse("speed=0.01 maxY=" + (origin.y + 1.0D)));
        helper.assertValueEqual(EventManager.activeId(), "voidrise", "active event id");
        helper.assertTrue(EventHooks.spawnCapMultiplier(MobCategory.MONSTER) == 1.0F,
                "the replaced blood moon must have released the spawn cap");
        EventManager.stop(StopReason.STOPPED);
        helper.succeed();
    }

    /**
     * {@code /event timer} retimes the running phase and {@code /event skip} past the last phase
     * ends the event - the two controls the whole recording workflow is built on.
     *
     * <p>Driven with {@code luckyrain} on purpose: it is the one event whose ticking does nothing
     * at all while no player is in the level, so it cannot disturb the tests running beside it.
     */
    public static void timerRetimesThePhaseAndSkipEndsTheEvent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));

        helper.assertTrue(EventManager.start("luckyrain", level.getServer(), level, origin, null,
                EventOptions.parse("duration=60 interval=20")), "luckyrain should start");
        EventPhase phase = EventManager.currentPhase();
        helper.assertTrue(phase != null, "a running event always has a phase");
        helper.assertValueEqual(phase.id(), "rain", "opening phase");
        helper.assertValueEqual(EventManager.phaseIndex(), 0, "opening phase index");
        helper.assertValueEqual(EventManager.currentPhaseDuration(), 1200,
                "duration=60 means 1200 ticks");

        helper.assertTrue(EventManager.timer(3), "/event timer should be accepted");
        helper.assertValueEqual(EventManager.currentPhaseDuration(), 60,
                "the timer overrides the current phase duration");

        helper.assertValueEqual(EventManager.skip(), "",
                "skipping the last phase reports no follow-up phase");
        helper.assertValueEqual(EventManager.activeId(), "",
                "skipping past the last phase ends the event");
        helper.assertTrue(EventManager.currentPhase() == null, "an idle director has no phase");
        helper.succeed();
    }

    /**
     * The meteor walks countdown to flight to impact under {@code /event skip}, and a stop in the
     * middle of a take leaves nothing of it behind.
     *
     * <p>Stops before the impact phase deliberately: the impact is a radius-6 explosion and a
     * radius-5 crater, which would reach outside this arena and into whatever test is running
     * next to it.
     */
    public static void meteorSkipsFromCountdownToFlight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));

        helper.assertTrue(EventManager.start("meteor", level.getServer(), level, origin, null,
                EventOptions.empty()), "meteor should start");
        helper.assertValueEqual(EventManager.currentPhase().id(), "countdown", "opening phase");
        helper.assertValueEqual(EventManager.skip(), "flight", "skip leaves the countdown");
        helper.assertValueEqual(EventManager.phaseIndex(), 1, "phase index after one skip");

        helper.assertTrue(EventManager.stop(StopReason.STOPPED), "meteor should stop");
        helper.assertValueEqual(EventManager.activeId(), "", "no event should be active");
        helper.assertEntityNotPresent(EventsFeature.meteorType());

        // Two takes back to back: the second start gets a fresh event, not the stopped one.
        helper.assertTrue(EventManager.start("meteor", level.getServer(), level, origin, null,
                EventOptions.empty()), "meteor should start a second time");
        helper.assertValueEqual(EventManager.currentPhase().id(), "countdown",
                "a restart begins at phase zero");
        helper.assertValueEqual(EventManager.phaseIndex(), 0, "a restart begins at phase zero");
        EventManager.stop(StopReason.STOPPED);
        helper.assertEntityNotPresent(EventsFeature.meteorType());
        helper.succeed();
    }

    /**
     * The lucky-rain payload: an {@code entity} outcome puts its mob in the world, and an outcome
     * naming something that does not exist is skipped rather than thrown - a data-pack typo must
     * not break a live take.
     */
    public static void luckyEntityOutcomeSpawnsItsMob(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));

        LuckyOutcome pigs = new LuckyOutcome(LuckyOutcome.TYPE_ENTITY, 1, 0,
                "minecraft:pig", "", 2, 0.0D, 0, 0, false);
        helper.assertTrue(pigs.isKnownType(), "the entity outcome type is runnable");
        helper.assertTrue(LuckyOutcomeRunner.run(level, pos, pigs, level.getRandom()),
                "an entity outcome should apply");
        helper.assertEntityPresent(EntityType.PIG);

        LuckyOutcome nonsense = new LuckyOutcome(LuckyOutcome.TYPE_ENTITY, 1, 0,
                "creator_events:definitely_not_a_mob", "", 1, 0.0D, 0, 0, false);
        helper.assertFalse(LuckyOutcomeRunner.run(level, pos, nonsense, level.getRandom()),
                "an unknown entity id is skipped, not thrown");

        LuckyOutcome emptyCommand = new LuckyOutcome(LuckyOutcome.TYPE_COMMAND, 1, 0,
                "", "   ", 1, 0.0D, 0, 0, false);
        helper.assertFalse(LuckyOutcomeRunner.run(level, pos, emptyCommand, level.getRandom()),
                "a blank command outcome does nothing");

        helper.killAllEntities();
        helper.succeed();
    }

    /**
     * The void plane rises, clamps at its ceiling and hands over to the hold phase. This is also
     * the one test that proves the director's per-tick driver is actually armed on this loader.
     *
     * <p>The plane is a level-wide height, so it is kept 20 blocks <em>below</em> the arena floor:
     * a test may never raise a kill plane into ground another test is standing on.
     */
    public static void voidPlaneClampsAndHandsOverToHold(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(1, 1, 1));
        Vec3 origin = Vec3.atCenterOf(floor);
        double minY = Math.max(level.getMinBuildHeight(), floor.getY() - 20);
        double maxY = minY + 1.0D;

        helper.assertTrue(EventManager.start("voidrise", level.getServer(), level, origin, null,
                        EventOptions.parse("minY=" + minY + " maxY=" + maxY + " speed=4")),
                "voidrise should start");
        WorldEvent started = EventManager.active();
        helper.assertTrue(started != null, "voidrise should be the active event");
        helper.assertTrue(Math.abs(started.voidY() - minY) < 1.0E-6D,
                "the plane starts at minY, was " + started.voidY());
        helper.assertValueEqual(EventManager.currentPhase().id(), "rise", "opening phase");

        helper.runAfterDelay(10L, () -> {
            WorldEvent running = EventManager.active();
            helper.assertTrue(running != null, "voidrise should still be running");
            helper.assertTrue(running.voidY() <= maxY + 1.0E-6D,
                    "the plane must never overshoot maxY, was " + running.voidY());
            helper.assertTrue(Math.abs(running.voidY() - maxY) < 1.0E-6D,
                    "the plane should have reached maxY by now, was " + running.voidY());
            EventPhase phase = EventManager.currentPhase();
            helper.assertTrue(phase != null && "hold".equals(phase.id()),
                    "a capped rise hands over to the hold phase, was "
                            + (phase == null ? "none" : phase.id()));
            helper.assertTrue(EventManager.stop(StopReason.STOPPED), "voidrise should stop");
            helper.succeed();
        });
    }

    private EventsGameTests() {
    }
}
