package dev.riftal.creator.features.powers;

import dev.riftal.creator.core.Feature;

/**
 * Power Kit - see {@code plans/03-power-kit.md}.
 *
 * <p>Player abilities on keybinds with cooldowns, a cooldown HUD row and server-validated activation.
 *
 * <p>This feature owns, and nothing else:
 * <ul>
 *   <li>{@code common/src/main/java/dev/riftal/creator/features/powers/**}</li>
 *   <li>{@code common/src/main/resources/assets/creator_powers/**} and {@code data/creator_powers/**}</li>
 *   <li>{@code common/src/main/resources/creatormods-powers.mixins.json}</li>
 *   <li>{@code common/src/test/java/dev/riftal/creator/features/powers/**}</li>
 *   <li>{@code fabric|neoforge/src/main/java/dev/riftal/creator/features/powers/**}</li>
 *   <li>{@code fabric/src/gametest/java/dev/riftal/creator/features/powers/**}</li>
 * </ul>
 * See {@code CONTRACT.md} in the repo root.
 */
public final class PowersFeature implements Feature {

    /** Feature id, resource namespace suffix and mixin config suffix. */
    public static final String ID = "powers";

    /** Resource namespace owned by this feature. */
    public static final String NAMESPACE = "creator_powers";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        // TODO(powers): declare Registrars, PlayerData attachments, Payloads and command trees here.
    }

    @Override
    public void initCommon() {
        // TODO(powers): loader-agnostic wiring that runs on both sides.
    }

    @Override
    public void initClient() {
        // TODO(powers): HUD layers, entity renderers, key mappings. Client only.
    }

    @Override
    public void initServer() {
        // TODO(powers): dedicated-server-only wiring, if any.
    }
}
