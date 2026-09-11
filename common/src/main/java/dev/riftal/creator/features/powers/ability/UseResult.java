package dev.riftal.creator.features.powers.ability;

/** Why an activation attempt did or did not happen. Returned by the server-side use path. */
public enum UseResult {

    /** The ability fired. */
    ACTIVATED,

    /** The player does not have that ability in a slot. */
    NOT_GRANTED,

    /** The ability id is not one of the six. */
    UNKNOWN,

    /** Still cooling down. */
    ON_COOLDOWN,

    /** {@code Ability#canUse} refused: wrong stance, no target, underwater, and so on. */
    CANNOT_USE;

    public boolean ok() {
        return this == ACTIVATED;
    }
}
