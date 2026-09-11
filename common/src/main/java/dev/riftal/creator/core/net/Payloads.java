package dev.riftal.creator.core.net;

import dev.riftal.creator.core.platform.CoreServices;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Loader-neutral custom payloads, over NeoForge's {@code PayloadRegistrar} and Fabric's
 * {@code PayloadTypeRegistry} / {@code ClientPlayNetworking}.
 *
 * <pre>{@code
 * public record UsePower(String abilityId) implements CustomPacketPayload {
 *     public static final Type<UsePower> TYPE = Payloads.type("creator_powers", "use_power");
 *     public static final StreamCodec<RegistryFriendlyByteBuf, UsePower> CODEC =
 *             StreamCodec.composite(ByteBufCodecs.STRING_UTF8, UsePower::abilityId, UsePower::new);
 *     public Type<? extends CustomPacketPayload> type() { return TYPE; }
 * }
 *
 * Payloads.registerC2S(UsePower.TYPE, UsePower.CODEC, (payload, sender) -> { ... });
 * Payloads.sendToServer(new UsePower("dash"));
 * }</pre>
 *
 * <p>Register from {@code Feature#registerContent()} on both sides - NeoForge flushes the
 * registrations inside {@code RegisterPayloadHandlersEvent} on the mod bus, which fires after mod
 * construction, so registering later misses the window.
 *
 * <p>Payload ids must live in your feature's namespace.
 *
 * <p><b>Sending never throws because of the receiver.</b> A player whose connection does not exist
 * yet, is going away, or never negotiated our channel (a fake player from another mod, a GameTest
 * stand-in, a client that has the feature switched off) is silently skipped by the loader helper
 * rather than blowing up the caller. Feature code routinely mirrors state to the client from a
 * scheduled task or an entity tick, and one unreachable player must not take that caller down -
 * on NeoForge {@code NetworkRegistry#checkPacket} throws for exactly this case, which used to
 * cancel the calling {@link dev.riftal.creator.core.sched.TickScheduler} task permanently.
 */
public final class Payloads {

    /** Builds a payload type id in your feature's namespace. */
    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String namespace, String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    /** Registers a client-to-server payload. The handler runs on the server thread. */
    public static <T extends CustomPacketPayload> void registerC2S(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            ServerHandler<T> handler) {
        CoreServices.NETWORK.registerC2S(type, codec, handler);
    }

    /** Registers a server-to-client payload. The handler runs on the client thread, client side only. */
    public static <T extends CustomPacketPayload> void registerS2C(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            ClientHandler<T> handler) {
        CoreServices.NETWORK.registerS2C(type, codec, handler);
    }

    /** Client to server. Client only. */
    public static void sendToServer(CustomPacketPayload payload) {
        CoreServices.NETWORK.sendToServer(payload);
    }

    /** Server to one player. A player who cannot receive it is skipped, not an error. */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        CoreServices.NETWORK.sendToPlayer(player, payload);
    }

    /** Server to every connected player. Players who cannot receive it are skipped. */
    public static void sendToAll(MinecraftServer server, CustomPacketPayload payload) {
        CoreServices.NETWORK.sendToAll(server, payload);
    }

    /**
     * Server to every player tracking {@code entity}, including the entity itself if it is a
     * player. Players who cannot receive it are skipped.
     */
    public static void sendToTracking(Entity entity, CustomPacketPayload payload) {
        CoreServices.NETWORK.sendToTracking(entity, payload);
    }

    private Payloads() {
    }
}
