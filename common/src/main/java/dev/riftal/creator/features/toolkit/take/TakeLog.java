package dev.riftal.creator.features.toolkit.take;

import static dev.riftal.creator.Constants.LOG;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Writes one plain-text log per take, next to the world:
 * {@code <server dir>/creator-toolkit/takes/<world>_<date>_take-003.log}.
 *
 * <p>Everything here is plain {@code java.nio}, so it is unit testable against a temp directory.
 * Writes are tiny (one line per event) and only happen on start / mark / stop, never per tick.
 */
public final class TakeLog {

    /** Sub-directory of the server directory that holds every take log. */
    public static final String DIRECTORY = "creator-toolkit";

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT).withZone(ZoneId.systemDefault());

    private final Path file;

    private TakeLog(Path file) {
        this.file = file;
    }

    /** The file this log writes to. */
    public Path file() {
        return file;
    }

    /** File name only, for command feedback that should not leak absolute paths on camera. */
    public String fileName() {
        return file.getFileName().toString();
    }

    /**
     * Opens (creating the directory tree) the log for one take and writes its header line.
     *
     * @param baseDirectory the server directory
     * @param worldName     raw level name; sanitised for the file name
     * @return the log, or null if the directory could not be created
     */
    public static TakeLog open(Path baseDirectory, String worldName, TakeState state) {
        String safeWorld = TakeFormat.sanitize(worldName);
        String date = DATE.format(Instant.ofEpochMilli(state.startEpochMs()));
        Path directory = baseDirectory.resolve(DIRECTORY).resolve("takes");
        Path target = directory.resolve(safeWorld + "_" + date + "_take-"
                + TakeFormat.takeNumber(state.number()) + ".log");
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            LOG.warn("[toolkit] could not create take log directory {}", directory, e);
            return null;
        }
        TakeLog log = new TakeLog(target);
        log.append("take=" + state.number()
                + " start=" + TakeFormat.isoInstant(state.startEpochMs())
                + " tick=" + state.startTick()
                + " world=" + safeWorld);
        return log;
    }

    /** Appends one mark line. */
    public void mark(Mark mark) {
        append(mark.toLogLine());
    }

    /** Appends the footer line and stops being useful. */
    public void close(TakeState stopped, long nowEpochMs) {
        append("stop=" + TakeFormat.isoInstant(nowEpochMs)
                + " rta=" + TakeFormat.formatRta(stopped.stoppedRtaMs())
                + " marks=" + stopped.marks().size());
    }

    /** Appends the footer line for a take that was interrupted rather than stopped cleanly. */
    public void closeAborted(TakeState stopped, long nowEpochMs, String reason) {
        append("stop=" + TakeFormat.isoInstant(nowEpochMs)
                + " rta=" + TakeFormat.formatRta(stopped.stoppedRtaMs())
                + " marks=" + stopped.marks().size()
                + " reason=" + reason);
    }

    private void append(String line) {
        try {
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warn("[toolkit] could not write take log {}", file, e);
        }
    }
}
