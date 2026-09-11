package dev.riftal.creator.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.core.util.CooldownTracker;
import org.junit.jupiter.api.Test;

class CooldownTrackerTest {

    @Test
    void unknownKeysAreAlwaysReady() {
        assertTrue(new CooldownTracker<String>().ready("dash", 0L));
    }

    @Test
    void cooldownElapsesAfterExactlyTheRequestedTicks() {
        CooldownTracker<String> cooldowns = new CooldownTracker<>();
        cooldowns.start("dash", 100L, 20);

        assertFalse(cooldowns.ready("dash", 119L));
        assertEquals(1, cooldowns.remaining("dash", 119L));
        assertTrue(cooldowns.ready("dash", 120L));
        assertEquals(0, cooldowns.remaining("dash", 120L));
    }

    @Test
    void progressIsAFractionOfTheFullLength() {
        CooldownTracker<String> cooldowns = new CooldownTracker<>();
        cooldowns.start("burst", 0L, 40);
        assertEquals(0.5F, cooldowns.progress("burst", 20L, 40), 1.0E-4F);
    }
}
