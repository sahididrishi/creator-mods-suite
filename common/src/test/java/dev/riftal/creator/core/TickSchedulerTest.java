package dev.riftal.creator.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.core.sched.ScheduledTask;
import dev.riftal.creator.core.sched.TickScheduler;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example of a pure-logic unit test: no game boot, no registries, runs in {@code ./gradlew :common:test}.
 * {@link TickScheduler#tickForTest(int)} advances the scheduler without a server.
 */
class TickSchedulerTest {

    @BeforeEach
    @AfterEach
    void reset() {
        TickScheduler.clear();
    }

    @Test
    void runLaterFiresExactlyOnceAtTheRequestedTick() {
        AtomicInteger runs = new AtomicInteger();
        TickScheduler.runLater(3, runs::incrementAndGet);

        TickScheduler.tickForTest(2);
        assertEquals(0, runs.get(), "must not fire early");

        TickScheduler.tickForTest(1);
        assertEquals(1, runs.get(), "must fire on the third tick");

        TickScheduler.tickForTest(20);
        assertEquals(1, runs.get(), "one-shot must not repeat");
        assertEquals(0, TickScheduler.size());
    }

    @Test
    void runRepeatingHonoursPeriodAndCount() {
        AtomicInteger runs = new AtomicInteger();
        TickScheduler.runRepeating(5, 3, runs::incrementAndGet);

        TickScheduler.tickForTest(15);
        assertEquals(3, runs.get());

        TickScheduler.tickForTest(50);
        assertEquals(3, runs.get(), "must stop after the requested number of runs");
    }

    @Test
    void taskCanCancelItself() {
        AtomicInteger runs = new AtomicInteger();
        TickScheduler.runRepeating(1, -1, (ScheduledTask task) -> {
            if (runs.incrementAndGet() == 4) {
                task.cancel();
            }
        });

        TickScheduler.tickForTest(100);
        assertEquals(4, runs.get());
    }

    @Test
    void cancelAllDropsOnlyTheTaggedTasks() {
        ResourceLocation mine = ResourceLocation.fromNamespaceAndPath("creator_events", "meteor");
        ResourceLocation theirs = ResourceLocation.fromNamespaceAndPath("creator_rules", "shuffle");

        AtomicInteger mineRuns = new AtomicInteger();
        AtomicInteger theirRuns = new AtomicInteger();
        TickScheduler.runRepeating(1, -1, mineRuns::incrementAndGet).tag(mine);
        TickScheduler.runRepeating(1, -1, theirRuns::incrementAndGet).tag(theirs);

        TickScheduler.tickForTest(3);
        assertEquals(1, TickScheduler.cancelAll(mine));
        TickScheduler.tickForTest(3);

        assertEquals(3, mineRuns.get());
        assertEquals(6, theirRuns.get());
    }

    @Test
    void aRepeatingTaskSurvivesATransientFailure() {
        AtomicInteger runs = new AtomicInteger();
        TickScheduler.runRepeating(1, -1, () -> {
            if (runs.incrementAndGet() == 2) {
                throw new IllegalStateException("one bad tick");
            }
        });

        TickScheduler.tickForTest(6);
        assertEquals(6, runs.get(), "a single throw must not disable a repeating task");
        assertEquals(1, TickScheduler.size(), "the task is still scheduled");
    }

    @Test
    void aTaskThatKeepsThrowingSpendsItsBudgetAndStops() {
        AtomicInteger runs = new AtomicInteger();
        TickScheduler.runRepeating(1, -1, () -> {
            runs.incrementAndGet();
            throw new IllegalStateException("boom");
        });

        TickScheduler.tickForTest(10);
        assertEquals(3, runs.get(), "cancelled once the consecutive-failure budget is spent");
        assertEquals(0, TickScheduler.size());
    }

    @Test
    void aThrowingTaskIsCancelledAndTheLoopSurvives() {
        AtomicInteger good = new AtomicInteger();
        TickScheduler.runRepeating(1, -1, () -> {
            throw new IllegalStateException("boom");
        });
        TickScheduler.runRepeating(1, -1, good::incrementAndGet);

        TickScheduler.tickForTest(5);
        assertTrue(good.get() >= 5, "a broken task must not stop the others");
    }
}
