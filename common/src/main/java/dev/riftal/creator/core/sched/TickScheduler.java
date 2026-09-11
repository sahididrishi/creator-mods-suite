package dev.riftal.creator.core.sched;

import dev.riftal.creator.Constants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Server-tick scheduler. Tasks run on the server thread at the end of a tick.
 *
 * <pre>{@code
 * TickScheduler.runLater(40, () -> level.explode(...));
 * TickScheduler.runRepeating(5, 20, () -> spawnRingParticle()).tag(rl("meteor"));
 * TickScheduler.cancelAll(rl("meteor"));
 * }</pre>
 *
 * <p>Semantics: exceptions are caught and logged with the owner tag - a broken feature must not
 * kill the tick loop. A repeating task is <em>not</em> dropped for a single throw: it has a budget
 * of 3 consecutive failures, reset by every run that completes, and is
 * cancelled only once it has used the budget up. One transient failure - a player who went offline
 * mid-send, a chunk that was not loaded this tick - must not silently disable a feature's
 * heartbeat for the rest of the server's life, while a task that is genuinely broken still stops
 * instead of throwing forever. The queue is cleared on server stop, so nothing is persisted; a
 * feature that must survive a restart keeps its own {@code SavedData} and re-schedules on load.
 *
 * <p>Pure Java, so it is unit-testable without booting a game: see {@link #tickForTest(int)}.
 */
public final class TickScheduler {

    /** Consecutive throws a repeating task may take before it is cancelled. */
    static final int FAILURE_BUDGET = 3;

    private static final List<ScheduledTask> TASKS = new ArrayList<>();
    private static final List<ScheduledTask> PENDING = new ArrayList<>();
    private static MinecraftServer server;
    private static boolean ticking;

    /** Runs {@code task} once, {@code delayTicks} ticks from now. 0 means next tick. */
    public static ScheduledTask runLater(int delayTicks, Runnable task) {
        return add(new ScheduledTask(t -> task.run(), delayTicks, 0, 1));
    }

    /** As {@link #runLater(int, Runnable)}; the server argument is accepted for readability. */
    public static ScheduledTask runLater(MinecraftServer server, int delayTicks, Runnable task) {
        TickScheduler.server = server;
        return runLater(delayTicks, task);
    }

    /**
     * Runs {@code task} every {@code periodTicks} ticks, {@code times} times.
     *
     * @param periodTicks ticks between runs, minimum 1
     * @param times       number of runs, or -1 to repeat until cancelled
     */
    public static ScheduledTask runRepeating(int periodTicks, int times, Runnable task) {
        return runRepeating(periodTicks, times, t -> task.run());
    }

    /** As above, but the task receives its own handle and can {@link ScheduledTask#cancel()} itself. */
    public static ScheduledTask runRepeating(int periodTicks, int times, Consumer<ScheduledTask> task) {
        int period = Math.max(1, periodTicks);
        return add(new ScheduledTask(task, period, period, times));
    }

    /** Cancels every task tagged with {@code owner}. Returns how many were cancelled. */
    public static int cancelAll(ResourceLocation owner) {
        int n = 0;
        for (ScheduledTask task : allTasks()) {
            if (owner.equals(task.owner) && !task.isDone()) {
                task.cancel();
                n++;
            }
        }
        return n;
    }

    /** Drops every queued task. Called by the loader on server stop. */
    public static void clear() {
        TASKS.clear();
        PENDING.clear();
        server = null;
    }

    /** The running server, or null outside a server session. */
    public static MinecraftServer server() {
        return server;
    }

    /** Number of live tasks. Handy in tests and in {@code /creator} diagnostics. */
    public static int size() {
        return TASKS.size() + PENDING.size();
    }

    /** Called by the loader's end-of-server-tick hook. */
    public static void tick(MinecraftServer server) {
        TickScheduler.server = server;
        tickOnce();
    }

    /** Advances the scheduler {@code ticks} ticks with no server attached. Unit tests only. */
    public static void tickForTest(int ticks) {
        for (int i = 0; i < ticks; i++) {
            tickOnce();
        }
    }

    private static ScheduledTask add(ScheduledTask task) {
        if (ticking) {
            PENDING.add(task);
        } else {
            TASKS.add(task);
        }
        return task;
    }

    private static void tickOnce() {
        ticking = true;
        try {
            Iterator<ScheduledTask> it = TASKS.iterator();
            while (it.hasNext()) {
                ScheduledTask task = it.next();
                if (task.cancelled) {
                    it.remove();
                    continue;
                }
                if (--task.ticksUntilRun > 0) {
                    continue;
                }
                Throwable failure = null;
                try {
                    task.action.accept(task);
                    task.consecutiveFailures = 0;
                } catch (Throwable t) {
                    failure = t;
                }
                if (task.repeatsLeft > 0) {
                    task.repeatsLeft--;
                }
                if (failure != null) {
                    noteFailure(task, failure);
                }
                if (task.cancelled || task.repeatsLeft == 0 || task.period <= 0) {
                    task.done = true;
                    it.remove();
                } else {
                    task.ticksUntilRun = task.period;
                }
            }
        } finally {
            ticking = false;
        }
        if (!PENDING.isEmpty()) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
    }

    /**
     * Logs a throw and spends one of the task's failure budget. A task that is finishing anyway -
     * a one-shot, or the last run of a repeat - has no budget to spend, so it is only reported.
     */
    private static void noteFailure(ScheduledTask task, Throwable failure) {
        if (task.cancelled || task.repeatsLeft == 0 || task.period <= 0) {
            Constants.LOG.error("Scheduled task (owner {}) threw on its final run", task.owner, failure);
            return;
        }
        task.consecutiveFailures++;
        if (task.consecutiveFailures >= FAILURE_BUDGET) {
            Constants.LOG.error("Scheduled task (owner {}) threw {} times in a row; cancelling it",
                    task.owner, task.consecutiveFailures, failure);
            task.cancelled = true;
        } else {
            Constants.LOG.error("Scheduled task (owner {}) threw ({} of {} allowed in a row); keeping it",
                    task.owner, task.consecutiveFailures, FAILURE_BUDGET, failure);
        }
    }

    private static List<ScheduledTask> allTasks() {
        List<ScheduledTask> all = new ArrayList<>(TASKS);
        all.addAll(PENDING);
        return all;
    }

    private TickScheduler() {
    }
}
