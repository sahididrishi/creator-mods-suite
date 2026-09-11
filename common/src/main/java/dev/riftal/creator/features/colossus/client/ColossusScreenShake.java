package dev.riftal.creator.features.colossus.client;

import dev.riftal.creator.features.colossus.net.ScreenShakePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side camera shake driven by {@link ScreenShakePayload}.
 *
 * <p>The server decides <em>that</em> the ground moved and how hard; this decides what that looks
 * like from where the player is standing. Strength falls off with distance and decays over the
 * life of the shake, so a slam twenty blocks away is a rumble and one at your feet is a jolt.
 *
 * <p>Read once per frame by {@code ColossusCameraMixin}. <b>Client only.</b>
 */
public final class ColossusScreenShake {

    /** Hard ceiling on the camera offset, in blocks. Keeps the effect readable, not nauseating. */
    public static final float MAX_OFFSET = 0.45F;

    private static float amplitude;
    private static double startTime;
    private static double endTime;

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
    }

    /** Drops any running shake. Called when the player leaves the world. */
    public static void clear() {
        amplitude = 0.0F;
        startTime = 0.0D;
        endTime = 0.0D;
    }

    /** True while there is something to offset the camera by. */
    public static boolean isActive() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            if (amplitude != 0.0F) {
                clear();
            }
            return false;
        }
        return amplitude > 0.0F && minecraft.level.getGameTime() < endTime;
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

    private static double time(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0.0D : minecraft.level.getGameTime() + partialTick;
    }

    private static double current(float partialTick) {
        double total = endTime - startTime;
        if (total <= 0.0D) {
            return 0.0D;
        }
        double remaining = (endTime - time(partialTick)) / total;
        if (remaining <= 0.0D) {
            return 0.0D;
        }
        // Quadratic decay: the first few ticks carry the punch.
        return amplitude * remaining * remaining;
    }

    private ColossusScreenShake() {
    }
}
