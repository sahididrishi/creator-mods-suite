package dev.riftal.creator.features.events.api;

/** Why a {@link WorldEvent} is being torn down. */
public enum StopReason {
    /** The last phase ran out on its own. */
    FINISHED,
    /** {@code /event stop}, or a new event replacing this one. */
    STOPPED,
    /** {@code /event skip} past the final phase. */
    SKIPPED,
    /** The server is shutting down; world changes stay, transient state is dropped. */
    SERVER_STOP
}
