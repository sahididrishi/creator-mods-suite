package dev.riftal.creator.features.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.toolkit.take.Mark;
import dev.riftal.creator.features.toolkit.take.TakeState;
import org.junit.jupiter.api.Test;

import java.util.List;

class TakeStateTest {

    @Test
    void idleTakeHasNoDuration() {
        TakeState idle = TakeState.idle(5);
        assertEquals(5, idle.number());
        assertFalse(idle.running());
        assertEquals(0L, idle.rtaMillis(System.currentTimeMillis()));
    }

    @Test
    void runningTakeCountsFromItsStart() {
        TakeState running = new TakeState(1, true, 1_000L, 0, 0L, List.of());
        assertEquals(2_500L, running.rtaMillis(3_500L));
        assertEquals(0L, running.rtaMillis(500L), "a clock that went backwards must not go negative");
    }

    @Test
    void stoppingFreezesTheDuration() {
        TakeState stopped = new TakeState(1, true, 1_000L, 0, 0L, List.of()).stopped(4_000L);
        assertFalse(stopped.running());
        assertEquals(3_000L, stopped.stoppedRtaMs());
        assertEquals(3_000L, stopped.rtaMillis(9_999_999L));
    }

    @Test
    void marksAreAppendedWithoutMutatingTheOldState() {
        TakeState before = new TakeState(1, true, 0L, 0, 0L, List.of());
        TakeState after = before.withMark(new Mark(1, 100L, 100L, 2, "Creator", ""));
        assertTrue(before.marks().isEmpty());
        assertEquals(1, after.marks().size());
        assertThrows(UnsupportedOperationException.class,
                () -> after.marks().add(new Mark(2, 1L, 1L, 1, "x", "")));
    }

    @Test
    void markLogLineCarriesEverything() {
        Mark mark = new Mark(2, 61_230L, 0L, 1660, "Creator", "note");
        String line = mark.toLogLine();
        assertTrue(line.startsWith("mark=2 rta=01:01.2 tick=1660 wall="), line);
        assertTrue(line.endsWith("by=Creator label=note"), line);
    }
}
