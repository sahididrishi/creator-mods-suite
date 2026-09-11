package dev.riftal.creator.features.colossus.net;

import dev.riftal.creator.core.net.ClientHandler;
import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.core.util.Selection;
import dev.riftal.creator.features.colossus.ColossusFeature;
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

    /** Does nothing until a client installs the real one. */
    private static volatile ClientHandler<ScreenShakePayload> clientHandler = payload -> {
    };

    /**
     * Installs the client-side receiver. Called from {@code ColossusFeature#initClient()} with
     * {@code ColossusScreenShake::begin}.
     *
     * <p>The indirection is not decoration. This class is class-initialised on a <b>dedicated
     * server</b> - {@code registerContent()} has to register the payload <em>type</em> on both
     * sides or the server cannot encode the packet it is about to send - and a class that is
     * loaded on a dedicated server must not name a client class anywhere, not even inside a method
     * body that never runs there. Lazy constant-pool resolution happens to save an ordinary JVM,
     * but an eager-resolution agent, an AOT or CDS archive or a class transformer turns it into a
     * {@code NoClassDefFoundError}. With the handler behind this field, the only things left in
     * this feature that name {@code ColossusScreenShake} are {@code ColossusFeature#initClient()}
     * and {@code ColossusCameraMixin}, which is listed under {@code "client"} in
     * {@code creatormods-colossus.mixins.json} and is therefore never applied on a dedicated
     * server at all.
     */
    public static void installClientHandler(ClientHandler<ScreenShakePayload> handler) {
        clientHandler = handler;
    }

    /**
     * Client receiver. Registered from {@code registerContent()} on both sides; on a dedicated
     * server it is never called, and would be a no-op if it were.
     */
    public static void handleOnClient(ScreenShakePayload payload) {
        clientHandler.handle(payload);
    }
}
