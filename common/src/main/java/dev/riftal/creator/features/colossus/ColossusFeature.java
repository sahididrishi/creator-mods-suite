package dev.riftal.creator.features.colossus;

import dev.riftal.creator.core.Feature;

/**
 * Ashen Colossus - see {@code plans/02-ashen-colossus.md}.
 *
 * <p>A three-phase GeckoLib boss with a boss bar, minion waves, an arena ring and a loot table.
 *
 * <p>This feature owns, and nothing else:
 * <ul>
 *   <li>{@code common/src/main/java/dev/riftal/creator/features/colossus/**}</li>
 *   <li>{@code common/src/main/resources/assets/creator_colossus/**} and {@code data/creator_colossus/**}</li>
 *   <li>{@code common/src/main/resources/creatormods-colossus.mixins.json}</li>
 *   <li>{@code common/src/test/java/dev/riftal/creator/features/colossus/**}</li>
 *   <li>{@code fabric|neoforge/src/main/java/dev/riftal/creator/features/colossus/**}</li>
 *   <li>{@code fabric/src/gametest/java/dev/riftal/creator/features/colossus/**}</li>
 * </ul>
 * See {@code CONTRACT.md} in the repo root.
 */
public final class ColossusFeature implements Feature {

    /** Feature id, resource namespace suffix and mixin config suffix. */
    public static final String ID = "colossus";

    /** Resource namespace owned by this feature. */
    public static final String NAMESPACE = "creator_colossus";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        // TODO(colossus): declare Registrars, PlayerData attachments, Payloads and command trees here.
    }

    @Override
    public void initCommon() {
        // TODO(colossus): loader-agnostic wiring that runs on both sides.
    }

    @Override
    public void initClient() {
        // TODO(colossus): HUD layers, entity renderers, key mappings. Client only.
    }

    @Override
    public void initServer() {
        // TODO(colossus): dedicated-server-only wiring, if any.
    }
}
