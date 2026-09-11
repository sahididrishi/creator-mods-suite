package dev.riftal.creator.features.arsenal;

import dev.riftal.creator.core.Feature;

/**
 * Arsenal - see {@code plans/07-arsenal.md}.
 *
 * <p>Signature weapons: a hammer, a scythe, a grapple and a storm bow, with custom projectiles.
 *
 * <p>This feature owns, and nothing else:
 * <ul>
 *   <li>{@code common/src/main/java/dev/riftal/creator/features/arsenal/**}</li>
 *   <li>{@code common/src/main/resources/assets/creator_arsenal/**} and {@code data/creator_arsenal/**}</li>
 *   <li>{@code common/src/main/resources/creatormods-arsenal.mixins.json}</li>
 *   <li>{@code common/src/test/java/dev/riftal/creator/features/arsenal/**}</li>
 *   <li>{@code fabric|neoforge/src/main/java/dev/riftal/creator/features/arsenal/**}</li>
 *   <li>{@code fabric/src/gametest/java/dev/riftal/creator/features/arsenal/**}</li>
 * </ul>
 * See {@code CONTRACT.md} in the repo root.
 */
public final class ArsenalFeature implements Feature {

    /** Feature id, resource namespace suffix and mixin config suffix. */
    public static final String ID = "arsenal";

    /** Resource namespace owned by this feature. */
    public static final String NAMESPACE = "creator_arsenal";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        // TODO(arsenal): declare Registrars, PlayerData attachments, Payloads and command trees here.
    }

    @Override
    public void initCommon() {
        // TODO(arsenal): loader-agnostic wiring that runs on both sides.
    }

    @Override
    public void initClient() {
        // TODO(arsenal): HUD layers, entity renderers, key mappings. Client only.
    }

    @Override
    public void initServer() {
        // TODO(arsenal): dedicated-server-only wiring, if any.
    }
}
