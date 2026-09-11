package dev.riftal.creator.features.events;

import dev.riftal.creator.core.Feature;

/**
 * Event Director - see {@code plans/06-event-director.md}.
 *
 * <p>Timed world events - blood moon, meteor shower, siege, lucky rain, void rise - that survive a relog.
 *
 * <p>This feature owns, and nothing else:
 * <ul>
 *   <li>{@code common/src/main/java/dev/riftal/creator/features/events/**}</li>
 *   <li>{@code common/src/main/resources/assets/creator_events/**} and {@code data/creator_events/**}</li>
 *   <li>{@code common/src/main/resources/creatormods-events.mixins.json}</li>
 *   <li>{@code common/src/test/java/dev/riftal/creator/features/events/**}</li>
 *   <li>{@code fabric|neoforge/src/main/java/dev/riftal/creator/features/events/**}</li>
 *   <li>{@code fabric/src/gametest/java/dev/riftal/creator/features/events/**}</li>
 * </ul>
 * See {@code CONTRACT.md} in the repo root.
 */
public final class EventsFeature implements Feature {

    /** Feature id, resource namespace suffix and mixin config suffix. */
    public static final String ID = "events";

    /** Resource namespace owned by this feature. */
    public static final String NAMESPACE = "creator_events";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        // TODO(events): declare Registrars, PlayerData attachments, Payloads and command trees here.
    }

    @Override
    public void initCommon() {
        // TODO(events): loader-agnostic wiring that runs on both sides.
    }

    @Override
    public void initClient() {
        // TODO(events): HUD layers, entity renderers, key mappings. Client only.
    }

    @Override
    public void initServer() {
        // TODO(events): dedicated-server-only wiring, if any.
    }
}
