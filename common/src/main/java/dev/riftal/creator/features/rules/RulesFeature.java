package dev.riftal.creator.features.rules;

import dev.riftal.creator.core.Feature;

/**
 * Rule Engine - see {@code plans/04-rule-engine.md}.
 *
 * <p>Stackable chaos rules toggled at runtime, listed on the HUD and persisted with the world.
 *
 * <p>This feature owns, and nothing else:
 * <ul>
 *   <li>{@code common/src/main/java/dev/riftal/creator/features/rules/**}</li>
 *   <li>{@code common/src/main/resources/assets/creator_rules/**} and {@code data/creator_rules/**}</li>
 *   <li>{@code common/src/main/resources/creatormods-rules.mixins.json}</li>
 *   <li>{@code common/src/test/java/dev/riftal/creator/features/rules/**}</li>
 *   <li>{@code fabric|neoforge/src/main/java/dev/riftal/creator/features/rules/**}</li>
 *   <li>{@code fabric/src/gametest/java/dev/riftal/creator/features/rules/**}</li>
 * </ul>
 * See {@code CONTRACT.md} in the repo root.
 */
public final class RulesFeature implements Feature {

    /** Feature id, resource namespace suffix and mixin config suffix. */
    public static final String ID = "rules";

    /** Resource namespace owned by this feature. */
    public static final String NAMESPACE = "creator_rules";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        // TODO(rules): declare Registrars, PlayerData attachments, Payloads and command trees here.
    }

    @Override
    public void initCommon() {
        // TODO(rules): loader-agnostic wiring that runs on both sides.
    }

    @Override
    public void initClient() {
        // TODO(rules): HUD layers, entity renderers, key mappings. Client only.
    }

    @Override
    public void initServer() {
        // TODO(rules): dedicated-server-only wiring, if any.
    }
}
