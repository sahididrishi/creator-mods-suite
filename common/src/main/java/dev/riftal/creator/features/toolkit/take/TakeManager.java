package dev.riftal.creator.features.toolkit.take;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.features.toolkit.ToolkitState;
import dev.riftal.creator.features.toolkit.net.TakeStatePayload;
import dev.riftal.creator.features.toolkit.net.ToolkitNet;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * The recorder. One take is live at a time, server wide, because a shoot has one clapperboard.
 *
 * <p>State is deliberately in memory only: a world that comes back after a crash comes back
 * <em>not</em> recording. What does persist is the take <em>number</em>, in {@link ToolkitState}, so
 * take 4 follows take 3 even across a restart.
 */
public final class TakeManager {

    private static TakeState state = TakeState.idle(1);
    private static TakeLog log;

    /** The live take. Never null. */
    public static TakeState state() {
        return state;
    }

    /** True between start and stop. */
    public static boolean running() {
        return state.running();
    }

    /**
     * Starts the next take and opens its log file.
     *
     * @return the new state, or null if a take was already running
     */
    public static TakeState start(MinecraftServer server) {
        if (state.running()) {
            return null;
        }
        ToolkitState saved = ToolkitState.get(server);
        int number = saved.claimTakeNumber();
        state = new TakeState(number, true, System.currentTimeMillis(), server.getTickCount(),
                0L, List.of());
        log = TakeLog.open(server.getServerDirectory(), server.getWorldData().getLevelName(), state);
        LOG.info("[toolkit] take {} started", TakeFormat.takeNumber(number));
        broadcast(server);
        return state;
    }

    /**
     * Stops the live take and writes the log footer.
     *
     * @return the stopped state, or null if nothing was running
     */
    public static TakeState stop(MinecraftServer server) {
        if (!state.running()) {
            return null;
        }
        long now = System.currentTimeMillis();
        state = state.stopped(now);
        if (log != null) {
            log.close(state, now);
        }
        LOG.info("[toolkit] take {} stopped after {} with {} mark(s)",
                TakeFormat.takeNumber(state.number()), TakeFormat.formatRta(state.stoppedRtaMs()),
                state.marks().size());
        broadcast(server);
        return state;
    }

    /**
     * Drops a mark on the live take.
     *
     * @param by    who pressed the key or typed the command
     * @param label optional free-text note, never null
     * @return the new mark, or null if no take is running
     */
    public static Mark mark(MinecraftServer server, String by, String label) {
        if (!state.running()) {
            return null;
        }
        long now = System.currentTimeMillis();
        Mark mark = new Mark(state.marks().size() + 1, state.rtaMillis(now), now,
                server.getTickCount(), by, label == null ? "" : label);
        state = state.withMark(mark);
        if (log != null) {
            log.mark(mark);
        }
        broadcast(server);
        return mark;
    }

    /** Sets the number the next {@code start} will claim, and mirrors it onto an idle HUD. */
    public static void setNextNumber(MinecraftServer server, int number) {
        ToolkitState saved = ToolkitState.get(server);
        saved.setNextTakeNumber(number);
        if (!state.running()) {
            state = TakeState.idle(saved.nextTakeNumber());
            broadcast(server);
        }
    }

    /** The number the next {@code start} will use. */
    public static int nextNumber(MinecraftServer server) {
        return ToolkitState.get(server).nextTakeNumber();
    }

    /** Finishes a running take without a clean stop, e.g. the server is shutting down. */
    public static void abort(MinecraftServer server, String reason) {
        if (!state.running()) {
            return;
        }
        long now = System.currentTimeMillis();
        state = state.stopped(now);
        if (log != null) {
            log.closeAborted(state, now, reason);
        }
        LOG.info("[toolkit] take {} aborted ({})", TakeFormat.takeNumber(state.number()), reason);
    }

    /** The log file of the current or last take, or null if none was opened. */
    public static TakeLog log() {
        return log;
    }

    /** Forgets everything about the live take. Called when the server this belongs to goes away. */
    public static void reset() {
        state = TakeState.idle(1);
        log = null;
    }

    /** Current state as a payload, stamped now. */
    public static TakeStatePayload payload() {
        return TakeStatePayload.of(state, System.currentTimeMillis());
    }

    private static void broadcast(MinecraftServer server) {
        ToolkitNet.sendAll(server, payload());
    }

    private TakeManager() {
    }
}
