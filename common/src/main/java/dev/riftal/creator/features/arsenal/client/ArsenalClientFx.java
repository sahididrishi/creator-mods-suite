package dev.riftal.creator.features.arsenal.client;

import dev.riftal.creator.features.arsenal.net.GrappleFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side reaction to {@link GrappleFxPayload}.
 *
 * <p>Purely cosmetic garnish: a denser particle burst than the server's own
 * {@code Fx.particles} call, landing on the exact tick the hook bit rather than on the next
 * entity-tracker update. <b>Audio is deliberately not played here</b> - the server already emits
 * {@code creator_arsenal:arsenal.hook_bite} and {@code minecraft:item.trident.return} positionally
 * to everyone nearby, and playing them again locally doubled the sound.
 *
 * <p><b>Client only</b> - the class is referenced solely from inside the payload handler lambda,
 * which never runs on a dedicated server.
 */
public final class ArsenalClientFx {

    /** Handles one hook effect packet on the client thread. */
    public static void play(GrappleFxPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Vec3 pos = payload.pos();
        switch (payload.state()) {
            case GrappleFxPayload.STATE_BITE -> {
                for (int i = 0; i < 8; i++) {
                    double spread = (level.random.nextDouble() - 0.5D) * 0.4D;
                    level.addParticle(ParticleTypes.CRIT, pos.x + spread, pos.y + spread, pos.z + spread,
                            0.0D, 0.05D, 0.0D);
                }
            }
            case GrappleFxPayload.STATE_RETURN -> {
                for (int i = 0; i < 4; i++) {
                    double spread = (level.random.nextDouble() - 0.5D) * 0.3D;
                    level.addParticle(ParticleTypes.SMOKE, pos.x + spread, pos.y + spread, pos.z + spread,
                            0.0D, 0.02D, 0.0D);
                }
            }
            default -> {
            }
        }
    }

    private ArsenalClientFx() {
    }
}
