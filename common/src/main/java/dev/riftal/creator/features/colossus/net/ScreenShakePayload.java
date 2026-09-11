package dev.riftal.creator.features.colossus.net;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.core.util.Selection;
import dev.riftal.creator.features.colossus.ColossusFeature;
import dev.riftal.creator.features.colossus.client.ColossusScreenShake;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server to client: "the ground just moved here". The client turns it into a camera offset that
 * falls off with distance.
 *
 * <p>Sent on slam impact, on every phase roar and on each footstep the boss takes near a player.
 * There is no client to server traffic in this feature.
 */
public record ScreenShakePayload(double x, double y, double z, float intensity, int durationTicks,
                                 float radius) implements CustomPacketPayload {

    public static final Type<ScreenShakePayload> TYPE =
            Payloads.type(ColossusFeature.NAMESPACE, "screen_shake");

    public static final StreamCodec<RegistryFriendlyByteBuf, ScreenShakePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, ScreenShakePayload::x,
                    ByteBufCodecs.DOUBLE, ScreenShakePayload::y,
                    ByteBufCodecs.DOUBLE, ScreenShakePayload::z,
                    ByteBufCodecs.FLOAT, ScreenShakePayload::intensity,
                    ByteBufCodecs.VAR_INT, ScreenShakePayload::durationTicks,
                    ByteBufCodecs.FLOAT, ScreenShakePayload::radius,
                    ScreenShakePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Shakes every player inside {@code radius} of {@code origin}. */
    public static void sendAround(ServerLevel level, Vec3 origin, float intensity,
                                  int durationTicks, float radius) {
        ScreenShakePayload payload =
                new ScreenShakePayload(origin.x, origin.y, origin.z, intensity, durationTicks, radius);
        for (ServerPlayer player : Selection.playersAround(level, origin, radius)) {
            Payloads.sendToPlayer(player, payload);
        }
    }

    /**
     * Client receiver. Registered from {@code registerContent()} so the payload <em>type</em> exists
     * on a dedicated server too (which is what lets the server encode it); the body below only ever
     * runs on a physical client, so touching the client-only shake state here is safe.
     */
    public static void handleOnClient(ScreenShakePayload payload) {
        ColossusScreenShake.begin(payload);
    }
}
