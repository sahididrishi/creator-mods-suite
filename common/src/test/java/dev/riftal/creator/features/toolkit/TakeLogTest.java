package dev.riftal.creator.features.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.toolkit.take.Mark;
import dev.riftal.creator.features.toolkit.take.TakeLog;
import dev.riftal.creator.features.toolkit.take.TakeState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class TakeLogTest {

    @TempDir
    Path serverDirectory;

    @Test
    void writesHeaderMarksAndFooter() throws IOException {
        TakeState state = new TakeState(3, true, 1_700_000_000_000L, 1660, 0L, List.of());
        TakeLog log = TakeLog.open(serverDirectory, "My World: Ep/1", state);
        assertNotNull(log);

        Mark first = new Mark(1, 1_500L, 1_700_000_001_500L, 1690, "Creator", "");
        Mark second = new Mark(2, 12_300L, 1_700_000_012_300L, 1906, "Creator", "hero shot");
        log.mark(first);
        log.mark(second);

        TakeState stopped = new TakeState(3, false, state.startEpochMs(), state.startTick(),
                41_700L, List.of(first, second));
        log.close(stopped, 1_700_000_041_700L);

        List<String> lines = Files.readAllLines(log.file());
        assertEquals(4, lines.size());
        assertTrue(lines.get(0).startsWith("take=3 start="), lines.get(0));
        assertTrue(lines.get(0).contains("world=My_World__Ep_1"), lines.get(0));
        assertTrue(lines.get(1).startsWith("mark=1 rta=00:01.5 tick=1690"), lines.get(1));
        assertTrue(lines.get(2).contains("label=hero shot"), lines.get(2));
        assertTrue(lines.get(3).startsWith("stop="), lines.get(3));
        assertTrue(lines.get(3).contains("rta=00:41.7 marks=2"), lines.get(3));
    }

    @Test
    void namesOneFilePerTakeNumber() {
        TakeState three = new TakeState(3, true, 1_700_000_000_000L, 0, 0L, List.of());
        TakeState four = new TakeState(4, true, 1_700_000_000_000L, 0, 0L, List.of());
        TakeLog logThree = TakeLog.open(serverDirectory, "world", three);
        TakeLog logFour = TakeLog.open(serverDirectory, "world", four);
        assertNotNull(logThree);
        assertNotNull(logFour);
        assertTrue(logThree.fileName().endsWith("take-003.log"), logThree.fileName());
        assertTrue(logFour.fileName().endsWith("take-004.log"), logFour.fileName());
        assertTrue(Files.exists(logThree.file()));
    }

    @Test
    void reshootingATakeNumberReplacesTheOldFileInsteadOfConcatenating() throws IOException {
        TakeState first = new TakeState(42, true, 1_700_000_000_000L, 0, 0L, List.of());
        TakeLog discarded = TakeLog.open(serverDirectory, "world", first);
        assertNotNull(discarded);
        Mark blown = new Mark(1, 1_000L, 1_700_000_001_000L, 20, "Creator", "blown take");
        discarded.mark(blown);
        discarded.close(new TakeState(42, false, first.startEpochMs(), 0, 1_000L, List.of(blown)),
                1_700_000_001_000L);

        TakeState retake = new TakeState(42, true, 1_700_000_100_000L, 0, 0L, List.of());
        TakeLog log = TakeLog.open(serverDirectory, "world", retake);
        assertNotNull(log);
        assertEquals(discarded.file(), log.file(), "the retake targets the same name");
        Mark good = new Mark(1, 2_000L, 1_700_000_102_000L, 60, "Creator", "keeper");
        log.mark(good);
        log.close(new TakeState(42, false, retake.startEpochMs(), 0, 2_000L, List.of(good)),
                1_700_000_102_000L);

        List<String> lines = Files.readAllLines(log.file());
        assertEquals(3, lines.size(), "one file is one take: header + 1 mark + footer, got " + lines);
        assertTrue(lines.get(0).startsWith("take=42 start="), lines.get(0));
        assertTrue(lines.get(1).contains("label=keeper"), lines.get(1));
        assertTrue(lines.get(2).startsWith("stop="), lines.get(2));
    }

    @Test
    void anAbortedTakeRecordsItsReason() throws IOException {
        TakeState state = new TakeState(7, true, 1_700_000_000_000L, 0, 0L, List.of());
        TakeLog log = TakeLog.open(serverDirectory, "world", state);
        assertNotNull(log);
        log.closeAborted(state.stopped(1_700_000_005_000L), 1_700_000_005_000L, "server_stop");
        List<String> lines = Files.readAllLines(log.file());
        assertTrue(lines.get(lines.size() - 1).endsWith("reason=server_stop"),
                lines.get(lines.size() - 1));
    }
}
