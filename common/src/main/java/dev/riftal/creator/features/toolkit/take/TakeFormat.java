package dev.riftal.creator.features.toolkit.take;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Timer and log-line formatting for the take recorder. Pure Java, unit tested.
 */
public final class TakeFormat {

    /** Characters that are never allowed in a log file name. */
    private static final String ILLEGAL = "\\/:*?\"<>| \t";

    /**
     * Real-time-attack duration as {@code mm:ss.t}, or {@code h:mm:ss.t} past the hour.
     *
     * <pre>
     * 0         -> "00:00.0"
     * 61_230    -> "01:01.2"
     * 3_599_990 -> "59:59.9"
     * 3_600_000 -> "1:00:00.0"
     * </pre>
     */
    public static String formatRta(long millis) {
        long clamped = Math.max(0L, millis);
        long tenths = clamped / 100L;
        long seconds = tenths / 10L;
        long tenth = tenths % 10L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        long hours = minutes / 60L;
        minutes %= 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d.%d", hours, minutes, seconds, tenth);
        }
        return String.format(Locale.ROOT, "%02d:%02d.%d", minutes, seconds, tenth);
    }

    /** Zero-padded take number, as it appears on the HUD and in file names: {@code 003}. */
    public static String takeNumber(int number) {
        return String.format(Locale.ROOT, "%03d", Math.max(0, number));
    }

    /** ISO-8601 instant, e.g. {@code 2026-09-11T14:02:11.450Z}. */
    public static String isoInstant(long epochMillis) {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis));
    }

    /**
     * Makes a world name safe for a file name: {@code "My World: Ep/1"} becomes
     * {@code "My_World__Ep_1"}. Never returns an empty string.
     */
    public static String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "world";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            out.append(ILLEGAL.indexOf(c) >= 0 || c < ' ' ? '_' : c);
        }
        return out.toString();
    }

    private TakeFormat() {
    }
}
