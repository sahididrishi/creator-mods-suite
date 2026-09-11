package dev.riftal.creator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod-wide constants. Shared file: feature agents read it, nobody edits it.
 */
public final class Constants {

    public static final String MOD_ID = "creatormods";
    public static final String MOD_NAME = "Creator Mods Suite";

    /**
     * The one logger for this mod. Every feature, every loader, every class logs through this:
     *
     * <pre>{@code
     * import static dev.riftal.creator.Constants.LOG;
     * LOG.info("[vault] altar primed at {}", pos);
     * }</pre>
     *
     * <p>Do <strong>not</strong> call {@link LoggerFactory#getLogger} anywhere else — a per-class
     * logger fragments the log the human reads on camera and makes feature output impossible to
     * filter. Prefix your messages with {@code [<feature id>]} instead, use SLF4J {@code {}}
     * placeholders rather than string concatenation, and never log once per tick.
     */
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    private Constants() {
    }
}
