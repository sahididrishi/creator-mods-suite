package dev.riftal.creator.features.events.client;

import dev.riftal.creator.features.events.net.EventStatePayload;
import net.minecraft.world.phys.Vec3;

/**
 * The client's mirror of the director's state, plus the tint interpolation the sky mixin reads.
 *
 * <p><b>Client only.</b> Touched from {@code EventsFeature#initClient()} and from the client-side
 * mixin; nothing on a dedicated server ever loads this class.
 *
 * <p>The server already ramps the tint strength over the event's own phases, so the extra client
 * lerp here is only there to smooth the once-a-second state packet into per-frame motion.
 */
public final class ClientEventState {

    /** How fast the rendered tint chases the last value the server sent, per frame-ish tick. */
    private static final float FOLLOW = 0.08F;

    private static volatile EventStatePayload state = EventStatePayload.IDLE;
    private static float shownStrength;

    /** Replaces the mirrored state. Called from the payload receiver on the client thread. */
    public static void accept(EventStatePayload payload) {
        state = payload == null ? EventStatePayload.IDLE : payload;
    }

    /** The last state the server sent. Never null. */
    public static EventStatePayload state() {
        return state;
    }

    /** True while an event is running and the director has not hidden the HUD line. */
    public static boolean hudVisible() {
        EventStatePayload current = state;
        return current.active() && current.hud();
    }

    /** Clears everything. Called when the player leaves a world. */
    public static void reset() {
        state = EventStatePayload.IDLE;
        shownStrength = 0.0F;
    }

    /**
     * Mixes {@code vanilla} toward the active event's tint and advances the follow lerp.
     *
     * @return the colour the sky should actually be drawn in
     */
    public static Vec3 tintColour(Vec3 vanilla) {
        EventStatePayload current = state;
        float target = current.active() ? clamp(current.tintStrength()) : 0.0F;
        shownStrength += (target - shownStrength) * FOLLOW;
        if (target <= 0.0F && shownStrength <= 0.002F) {
            shownStrength = 0.0F;
        }
        if (shownStrength <= 0.0F) {
            return vanilla;
        }
        double t = shownStrength;
        return new Vec3(
                vanilla.x + (current.tintR() - vanilla.x) * t,
                vanilla.y + (current.tintG() - vanilla.y) * t,
                vanilla.z + (current.tintB() - vanilla.z) * t);
    }

    /** The strength currently being rendered, after the follow lerp. Read by the HUD. */
    public static float shownStrength() {
        return shownStrength;
    }

    private static float clamp(float value) {
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }

    private ClientEventState() {
    }
}
