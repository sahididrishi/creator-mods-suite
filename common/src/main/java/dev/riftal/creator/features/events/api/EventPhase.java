package dev.riftal.creator.features.events.api;

/**
 * One named stage of a {@link WorldEvent}.
 *
 * @param id            stable, lowercase; used for the {@code phase.creator_events.<id>} lang key
 * @param durationTicks how long the phase lasts, or {@code -1} for "until the event says so"
 */
public record EventPhase(String id, int durationTicks) {

    /** A phase that ends only when {@link WorldEvent#isPhaseComplete} says it does. */
    public static EventPhase open(String id) {
        return new EventPhase(id, -1);
    }

    /** A phase that ends after a fixed number of ticks. */
    public static EventPhase ticks(String id, int durationTicks) {
        return new EventPhase(id, Math.max(1, durationTicks));
    }

    /** A phase that ends after a fixed number of seconds. */
    public static EventPhase seconds(String id, int seconds) {
        return ticks(id, seconds * 20);
    }

    /** True when this phase has no tick-based end. */
    public boolean isOpen() {
        return durationTicks < 0;
    }
}
