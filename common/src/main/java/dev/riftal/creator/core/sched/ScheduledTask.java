package dev.riftal.creator.core.sched;

import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/** A handle on one job queued with {@link TickScheduler}. */
public final class ScheduledTask {

    final Consumer<ScheduledTask> action;
    final int period;
    int ticksUntilRun;
    int repeatsLeft;
    ResourceLocation owner;
    boolean cancelled;
    boolean done;

    ScheduledTask(Consumer<ScheduledTask> action, int delay, int period, int repeats) {
        this.action = action;
        this.ticksUntilRun = Math.max(0, delay);
        this.period = period;
        this.repeatsLeft = repeats;
    }

    /**
     * Tags the task with an owner so {@link TickScheduler#cancelAll(ResourceLocation)} can drop it.
     * Use your feature's namespace, e.g. {@code rl("bloodmoon")}.
     */
    public ScheduledTask tag(ResourceLocation owner) {
        this.owner = owner;
        return this;
    }

    public ResourceLocation owner() {
        return owner;
    }

    /** Stops the task. Safe to call from inside the task itself. */
    public void cancel() {
        this.cancelled = true;
    }

    /** True once the task has finished all its runs, was cancelled, or threw. */
    public boolean isDone() {
        return done || cancelled;
    }

    /** Ticks left before the next run. */
    public int remainingTicks() {
        return ticksUntilRun;
    }

    /** Runs left, or -1 when the task repeats forever. */
    public int remainingRuns() {
        return repeatsLeft;
    }
}
