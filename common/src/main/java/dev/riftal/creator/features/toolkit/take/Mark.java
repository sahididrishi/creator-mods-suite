package dev.riftal.creator.features.toolkit.take;

/**
 * One marker dropped during a take. Immutable.
 *
 * @param index      1-based position within the take
 * @param rtaMillis  wall-clock milliseconds since the take started
 * @param epochMillis wall-clock time the mark was taken
 * @param serverTick the server tick the mark landed on
 * @param by         the player (or "server") that placed it
 * @param label      free-text note, possibly empty
 */
public record Mark(int index, long rtaMillis, long epochMillis, int serverTick, String by, String label) {

    /** One log line: {@code mark=2 rta=01:01.2 tick=1660 wall=... by=Creator label=...}. */
    public String toLogLine() {
        StringBuilder line = new StringBuilder()
                .append("mark=").append(index)
                .append(" rta=").append(TakeFormat.formatRta(rtaMillis))
                .append(" tick=").append(serverTick)
                .append(" wall=").append(TakeFormat.isoInstant(epochMillis))
                .append(" by=").append(by);
        if (!label.isEmpty()) {
            line.append(" label=").append(label);
        }
        return line.toString();
    }
}
