package dev.riftal.creator.features.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.rules.rules.StillTracker;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

/** {@code no_stop_moving}'s "has this player moved?" bookkeeping. */
class StillTrackerTest {

    private static final UUID PLAYER = UUID.nameUUIDFromBytes("player".getBytes());

    @Test
    void standingStillIncrementsTheCounter() {
        StillTracker tracker = new StillTracker();
        tracker.update(PLAYER, 0.0D, 64.0D, 0.0D);

        assertEquals(1, tracker.update(PLAYER, 0.0D, 64.0D, 0.0D));
        assertEquals(2, tracker.update(PLAYER, 0.0D, 64.0D, 0.0D));
        assertEquals(3, tracker.update(PLAYER, 0.0D, 64.0D, 0.0D));
    }

    @Test
    void jitterUnderTwoCentimetresStillCountsAsStill() {
        StillTracker tracker = new StillTracker();
        tracker.update(PLAYER, 0.0D, 64.0D, 0.0D);

        assertEquals(1, tracker.update(PLAYER, 0.005D, 64.0D, 0.005D));
    }

    @Test
    void realMovementResetsTheCounter() {
        StillTracker tracker = new StillTracker();
        tracker.update(PLAYER, 0.0D, 64.0D, 0.0D);
        tracker.update(PLAYER, 0.0D, 64.0D, 0.0D);
        tracker.update(PLAYER, 0.0D, 64.0D, 0.0D);

        assertEquals(0, tracker.update(PLAYER, 1.0D, 64.0D, 0.0D));
    }

    @Test
    void graceKeepsTheCounterNegativeForThatManyTicks() {
        StillTracker tracker = new StillTracker();
        tracker.grace(PLAYER, 5);

        assertEquals(-5, tracker.stillTicks(PLAYER));
        assertEquals(-5, tracker.update(PLAYER, 0.0D, 64.0D, 0.0D), "the first tick only records a position");

        int value = -5;
        for (int i = 0; i < 5; i++) {
            value = tracker.update(PLAYER, 0.0D, 64.0D, 0.0D);
        }
        assertEquals(0, value, "five still ticks should burn exactly five ticks of grace");
    }

    @Test
    void forgettingAndRetainingDropPlayers() {
        StillTracker tracker = new StillTracker();
        UUID other = UUID.nameUUIDFromBytes("other".getBytes());
        tracker.update(PLAYER, 0.0D, 0.0D, 0.0D);
        tracker.update(other, 0.0D, 0.0D, 0.0D);
        assertEquals(2, tracker.size());

        tracker.retainAll(Set.of(PLAYER));
        assertEquals(1, tracker.size());

        tracker.forget(PLAYER);
        assertTrue(tracker.size() == 0);
    }
}
