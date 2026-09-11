package dev.riftal.creator.features.colossus.client;

import dev.riftal.creator.core.util.MathUtil;
import dev.riftal.creator.features.colossus.net.ScreenShakePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;

/**
 * Client-side camera shake driven by {@link ScreenShakePayload}.
 *
 * <p>The server decides <em>that</em> the ground moved and how hard; this decides what that looks
 * like from where the player is standing. Strength falls off with distance and decays over the
 * life of the shake, so a slam twenty blocks away is a rumble and one at your feet is a jolt.
 *
 * <p>Read once per frame by {@code ColossusCameraMixin}. <b>Client only.</b>
 *
 * <h2>Why the window is validated on every read</h2>
 *
 * <p>The window is expressed in the level's own game time, which is <em>not</em> monotonic across a
 * session: every world and every server has its own clock, and leaving one for another can move it
 * by millions of ticks in either direction. A shake left running in world A at game time 1,000,000
 * would, on joining a fresh world B at game time 0, read as "999,988 ticks of shake still to go" -
 * a camera offset of billions of blocks and a black screen for the rest of the session. So
 * {@link #isActive()} rejects, and clears, any window that does not belong to the level being
 * rendered right now: a different {@code ClientLevel} instance, a clock that has moved backwards
 * past the start of the shake, or a window that has already expired. The decay curve is clamped on
 * top of that, so even a clock nobody anticipated can never produce an offset larger than
 * {@link #MAX_OFFSET}.
 */
public final class ColossusScreenShake {

    /** Hard ceiling on the camera offset, in blocks. Keeps the effect readable, not nauseating. */
    public static final float MAX_OFFSET = 0.45F;

    private static float amplitude;
    private static double startTime;
    private static double endTime;

    /**
     * The level the running shake was started in. Weak on purpose: this class outlives every world,
     * and a strong reference here would pin a whole {@code ClientLevel} - entities, chunk cache and
     * all - for the rest of the session.
     */
    private static WeakReference<Object> levelRef = new WeakReference<>(null);

    /** Called by the payload handler on the client thread. */
    public static void begin(ScreenShakePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        double radius = Math.max(1.0D, payload.radius());
        double distance = player.position().distanceTo(new Vec3(payload.x(), payload.y(), payload.z()));
        if (distance > radius) {
            return;
        }
        float falloff = (float) (1.0D - distance / radius);
        float strength = Math.min(MAX_OFFSET, payload.intensity() * falloff * falloff);
        if (strength <= 0.001F) {
            return;
        }

        // A window inherited from another world is not a window at all - drop it before deciding
        // whether this shake is "bigger than what is already running".
        if (!windowBelongsTo(minecraft)) {
            clear();
        }

        double now = minecraft.level.getGameTime();
        double candidateEnd = now + Math.max(1, payload.durationTicks());
        // A bigger shake always wins; a smaller one only extends a fading tail.
        if (strength >= amplitude || now >= endTime) {
            amplitude = strength;
            startTime = now;
            endTime = candidateEnd;
        } else {
            endTime = Math.max(endTime, candidateEnd);
        }
        levelRef = new WeakReference<>(minecraft.level);
    }

    /** Drops any running shake. Safe to call at any time, from the client thread. */
    public static void clear() {
        amplitude = 0.0F;
        startTime = 0.0D;
        endTime = 0.0D;
        levelRef = new WeakReference<>(null);
    }

    /** True while there is something to offset the camera by. */
    public static boolean isActive() {
        if (amplitude <= 0.0F) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !windowBelongsTo(minecraft)
                || minecraft.level.getGameTime() >= endTime) {
            clear();
            return false;
        }
        return true;
    }

    /** Sideways offset, in blocks. */
    public static float offsetX(float partialTick) {
        return (float) (Math.sin(time(partialTick) * 2.31D) * current(partialTick));
    }

    /** Vertical offset, in blocks. */
    public static float offsetY(float partialTick) {
        return (float) (Math.cos(time(partialTick) * 3.13D) * current(partialTick));
    }

    /** Forward/back offset, in blocks. Deliberately weaker - it reads as a punch, not a zoom. */
    public static float offsetZoom(float partialTick) {
        return (float) (Math.sin(time(partialTick) * 1.71D) * current(partialTick) * 0.5D);
    }

    /**
     * True when the running window was started in the level that is on screen now, and that level's
     * clock has not moved backwards past the start of the shake.
     */
    private static boolean windowBelongsTo(Minecraft minecraft) {
        if (minecraft.level == null || levelRef.get() != minecraft.level) {
            return false;
        }
        return minecraft.level.getGameTime() >= startTime;
    }

    private static double time(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0.0D : minecraft.level.getGameTime() + partialTick;
    }

    private static double current(float partialTick) {
        double total = endTime - startTime;
        if (total <= 0.0D) {
            return 0.0D;
        }
        // Clamped, not merely checked for <= 0: a clock that disagrees with the window must decay
        // the shake to nothing or leave it at full strength, never amplify it.
        double remaining = MathUtil.clamp((endTime - time(partialTick)) / total, 0.0D, 1.0D);
        if (remaining <= 0.0D) {
            return 0.0D;
        }
        // Quadratic decay: the first few ticks carry the punch.
        return amplitude * remaining * remaining;
    }

    private ColossusScreenShake() {
    }
}
