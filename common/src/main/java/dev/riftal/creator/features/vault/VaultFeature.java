package dev.riftal.creator.features.vault;

import dev.riftal.creator.core.Feature;

/**
 * Cursed Vault - see {@code plans/08-cursed-vault.md}.
 *
 * <p>A generated structure with a keyed altar, a keeper fight and a sealed reward chest.
 *
 * <p>This feature owns, and nothing else:
 * <ul>
 *   <li>{@code common/src/main/java/dev/riftal/creator/features/vault/**}</li>
 *   <li>{@code common/src/main/resources/assets/creator_vault/**} and {@code data/creator_vault/**}</li>
 *   <li>{@code common/src/main/resources/creatormods-vault.mixins.json}</li>
 *   <li>{@code common/src/test/java/dev/riftal/creator/features/vault/**}</li>
 *   <li>{@code fabric|neoforge/src/main/java/dev/riftal/creator/features/vault/**}</li>
 *   <li>{@code fabric/src/gametest/java/dev/riftal/creator/features/vault/**}</li>
 * </ul>
 * See {@code CONTRACT.md} in the repo root.
 */
public final class VaultFeature implements Feature {

    /** Feature id, resource namespace suffix and mixin config suffix. */
    public static final String ID = "vault";

    /** Resource namespace owned by this feature. */
    public static final String NAMESPACE = "creator_vault";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        // TODO(vault): declare Registrars, PlayerData attachments, Payloads and command trees here.
    }

    @Override
    public void initCommon() {
        // TODO(vault): loader-agnostic wiring that runs on both sides.
    }

    @Override
    public void initClient() {
        // TODO(vault): HUD layers, entity renderers, key mappings. Client only.
    }

    @Override
    public void initServer() {
        // TODO(vault): dedicated-server-only wiring, if any.
    }
}
