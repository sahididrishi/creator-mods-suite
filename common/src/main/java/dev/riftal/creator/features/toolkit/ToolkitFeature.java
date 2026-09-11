package dev.riftal.creator.features.toolkit;

import dev.riftal.creator.core.Feature;

/**
 * Director's Toolkit - see {@code plans/01-directors-toolkit.md}.
 *
 * <p>Recording controls: take timer, freeze, wave spawning, arena snapshot and restore, camera paths, HUD overlay.
 *
 * <p>This feature owns, and nothing else:
 * <ul>
 *   <li>{@code common/src/main/java/dev/riftal/creator/features/toolkit/**}</li>
 *   <li>{@code common/src/main/resources/assets/creator_toolkit/**} and {@code data/creator_toolkit/**}</li>
 *   <li>{@code common/src/main/resources/creatormods-toolkit.mixins.json}</li>
 *   <li>{@code common/src/test/java/dev/riftal/creator/features/toolkit/**}</li>
 *   <li>{@code fabric|neoforge/src/main/java/dev/riftal/creator/features/toolkit/**}</li>
 *   <li>{@code fabric/src/gametest/java/dev/riftal/creator/features/toolkit/**}</li>
 * </ul>
 * See {@code CONTRACT.md} in the repo root.
 */
public final class ToolkitFeature implements Feature {

    /** Feature id, resource namespace suffix and mixin config suffix. */
    public static final String ID = "toolkit";

    /** Resource namespace owned by this feature. */
    public static final String NAMESPACE = "creator_toolkit";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        // TODO(toolkit): declare Registrars, PlayerData attachments, Payloads and command trees here.
    }

    @Override
    public void initCommon() {
        // TODO(toolkit): loader-agnostic wiring that runs on both sides.
    }

    @Override
    public void initClient() {
        // TODO(toolkit): HUD layers, entity renderers, key mappings. Client only.
    }

    @Override
    public void initServer() {
        // TODO(toolkit): dedicated-server-only wiring, if any.
    }
}
