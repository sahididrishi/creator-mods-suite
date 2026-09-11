package dev.riftal.creator.features.vault.block;

import net.minecraft.util.StringRepresentable;

/**
 * Lifecycle of one Cursed Altar. Mirrored into the blockstate property
 * {@code creator_vault:cursed_altar[state=...]} so the client gets it for free with the normal
 * block update, without a payload.
 *
 * <p>Pure logic: no Minecraft registry is touched, so this is safe in a JUnit test.
 */
public enum AltarState implements StringRepresentable {

    /** Armed, waiting for a Vault Key. */
    SEALED("sealed"),
    /** Key accepted, counting down to the summon. */
    CHARGING("charging"),
    /** The Vault Keeper is alive and bound to this altar. */
    ACTIVE("active"),
    /** The Keeper died, the chests are open. Needs {@code /vault reset} to re-arm. */
    SPENT("spent");

    private final String serializedName;

    AltarState(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    /** Parses a serialized name. Unknown or null input falls back to {@link #SEALED}. */
    public static AltarState byName(String name) {
        if (name != null) {
            for (AltarState state : values()) {
                if (state.serializedName.equals(name)) {
                    return state;
                }
            }
        }
        return SEALED;
    }

    /** True while the altar's block-entity ticker has work to do. */
    public boolean isTicking() {
        return this == CHARGING || this == ACTIVE;
    }
}
