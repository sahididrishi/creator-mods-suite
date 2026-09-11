package dev.riftal.creator.features.evolve;

import dev.riftal.creator.core.Feature;

/**
 * Evolve - see {@code plans/05-evolve.md}.
 *
 * <p>Player evolution stages that change size, stats and perks, with a transformation sequence.
 *
 * <p>This feature owns, and nothing else:
 * <ul>
 *   <li>{@code common/src/main/java/dev/riftal/creator/features/evolve/**}</li>
 *   <li>{@code common/src/main/resources/assets/creator_evolve/**} and {@code data/creator_evolve/**}</li>
 *   <li>{@code common/src/main/resources/creatormods-evolve.mixins.json}</li>
 *   <li>{@code common/src/test/java/dev/riftal/creator/features/evolve/**}</li>
 *   <li>{@code fabric|neoforge/src/main/java/dev/riftal/creator/features/evolve/**}</li>
 *   <li>{@code fabric/src/gametest/java/dev/riftal/creator/features/evolve/**}</li>
 * </ul>
 * See {@code CONTRACT.md} in the repo root.
 */
public final class EvolveFeature implements Feature {

    /** Feature id, resource namespace suffix and mixin config suffix. */
    public static final String ID = "evolve";

    /** Resource namespace owned by this feature. */
    public static final String NAMESPACE = "creator_evolve";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        // TODO(evolve): declare Registrars, PlayerData attachments, Payloads and command trees here.
    }

    @Override
    public void initCommon() {
        // TODO(evolve): loader-agnostic wiring that runs on both sides.
    }

    @Override
    public void initClient() {
        // TODO(evolve): HUD layers, entity renderers, key mappings. Client only.
    }

    @Override
    public void initServer() {
        // TODO(evolve): dedicated-server-only wiring, if any.
    }
}
