package dev.riftal.creator.features.events.client;

import dev.riftal.creator.features.events.net.EventStatePayload;
import net.minecraft.world.phys.Vec3;

/**
 * The client's mirror of the director's state, plus the tint interpolation the sky and fog hooks
 * read.
 *
 * <p><b>Client only in practice</b>, but deliberately free of every {@code net.minecraft.client}
 * type: {@code EventsFeature#registerContent()} names {@link #accept} on both sides, so this class
 * is loaded on a dedicated server too. Everything the viewer's own position or dimension is needed
 * for is pushed in through {@link #clientTick(double, String)} by the client-side mixin.
 *
 * <p>The follow lerp is advanced from that 20 Hz client tick and <em>not</em> from the render path:
 * driving it per frame made the blood-moon ramp four times faster on a 240 fps capture rig than on
 * a 60 fps one, and froze it entirely whenever the sky was not drawn (underwater, in the Nether,
 * on the pause screen).
 */
public final class ClientEventState {

    /** How fast the rendered tint chases the last value the server sent, per client tick. */
    private static final float FOLLOW = 0.08F;

    /** Above this many blocks over the void plane the voidrise tint has faded out completely. */
    private static final double VOID_TINT_RANGE = 48.0D;

    private static volatile EventStatePayload state = EventStatePayload.IDLE;
    private static float shownStrength;
    private static double viewerY = Double.NaN;
    private static String viewerDimension = "";

    /** Replaces the mirrored state. Called from the payload receiver on the client thread. */
    public static void accept(EventStatePayload payload) {
        state = payload == null ? EventStatePayload.IDLE : payload;
    }

    /** The last state the server sent. Never null. */
    public static EventStatePayload state() {
        return state;
    }

    /**
     * True while an event is running in the viewer's own dimension and the director has not hidden
     * the HUD line.
     */
    public static boolean hudVisible() {
        EventStatePayload current = state;
        return current.active() && current.hud() && current.appliesTo(viewerDimension);
    }

    /** Clears everything. Called when the player leaves a world, from the client mixin. */
    public static void reset() {
        state = EventStatePayload.IDLE;
        shownStrength = 0.0F;
        viewerY = Double.NaN;
        viewerDimension = "";
    }

    /**
     * One 20 Hz step of the tint follow, plus the viewer context the state needs.
     *
     * @param cameraY     the local player's Y, or {@link Double#NaN} when there is no player
     * @param dimensionId the local level's dimension id, or {@code ""} when there is no level
     */
    public static void clientTick(double cameraY, String dimensionId) {
        viewerY = cameraY;
        viewerDimension = dimensionId == null ? "" : dimensionId;
        float target = targetStrength();
        shownStrength += (target - shownStrength) * FOLLOW;
        if (target <= 0.0F && shownStrength <= 0.002F) {
            shownStrength = 0.0F;
        }
    }

    /**
     * The strength the tint is heading for: what the server sent, scaled by how close the viewer is
     * to the void plane when the running event has one.
     *
     * <p>{@code voidrise} sends a flat 0.3; without this, starting it with the plane 100 blocks
     * below the camera darkens the whole sky for no visible reason. The plan puts this computation
     * on the client ("scaling with proximity - client computes from {@code voidY} in the payload"),
     * because only the client knows where the camera is between state packets.
     */
    public static float targetStrength() {
        EventStatePayload current = state;
        if (!current.active() || !current.appliesTo(viewerDimension)) {
            return 0.0F;
        }
        float target = clamp(current.tintStrength());
        double plane = current.voidY();
        if (Double.isNaN(plane) || Double.isNaN(viewerY)) {
            return target;
        }
        double above = viewerY - plane;
        if (above <= 0.0D) {
            return target;
        }
        if (above >= VOID_TINT_RANGE) {
            return 0.0F;
        }
        return target * (float) (1.0D - above / VOID_TINT_RANGE);
    }

    /**
     * Mixes {@code vanilla} toward the active event's tint. A pure read: the follow lerp is
     * advanced by {@link #clientTick}, so this may be called once per frame or fifty times and the
     * fade takes the same wall-clock time either way.
     *
     * @return the colour the sky or fog should actually be drawn in
     */
    public static Vec3 tintColour(Vec3 vanilla) {
        EventStatePayload current = state;
        if (shownStrength <= 0.0F) {
            return vanilla;
        }
        double t = shownStrength;
        return new Vec3(
                vanilla.x + (current.tintR() - vanilla.x) * t,
                vanilla.y + (current.tintG() - vanilla.y) * t,
                vanilla.z + (current.tintB() - vanilla.z) * t);
    }

    /** The strength currently being rendered, after the follow lerp. Read by the HUD and the fog. */
    public static float shownStrength() {
        return shownStrength;
    }

    /** Sound event id the client should be looping, or {@code ""}. */
    public static String ambientLoop() {
        EventStatePayload current = state;
        return current.active() && current.appliesTo(viewerDimension) ? current.ambientLoop() : "";
    }

    private static float clamp(float value) {
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }

    private ClientEventState() {
    }
}
