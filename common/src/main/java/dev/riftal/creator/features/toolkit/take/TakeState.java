package dev.riftal.creator.features.toolkit.take;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of the recorder. Stored values are never mutated in place - every transition
 * returns a new instance.
 *
 * @param number        take number, 1-based
 * @param running       true between {@code /toolkit take start} and {@code stop}
 * @param startEpochMs  wall-clock start, 0 when the take never ran
 * @param startTick     server tick the take started on
 * @param stoppedRtaMs  final duration once stopped
 * @param marks         marks placed so far, in order
 */
public record TakeState(int number, boolean running, long startEpochMs, int startTick,
                        long stoppedRtaMs, List<Mark> marks) {

    public TakeState {
        marks = List.copyOf(marks);
    }

    /** A take that has a number but has never been started. */
    public static TakeState idle(int number) {
        return new TakeState(number, false, 0L, 0, 0L, List.of());
    }

    /** Duration to show: live wall clock while running, the frozen total once stopped. */
    public long rtaMillis(long nowEpochMs) {
        if (!running) {
            return stoppedRtaMs;
        }
        return Math.max(0L, nowEpochMs - startEpochMs);
    }

    /** Copy with one more mark appended. */
    public TakeState withMark(Mark mark) {
        List<Mark> updated = new ArrayList<>(marks);
        updated.add(mark);
        return new TakeState(number, running, startEpochMs, startTick, stoppedRtaMs, updated);
    }

    /** Copy, stopped, with the final duration baked in. */
    public TakeState stopped(long nowEpochMs) {
        return new TakeState(number, false, startEpochMs, startTick, rtaMillis(nowEpochMs), marks);
    }
}
