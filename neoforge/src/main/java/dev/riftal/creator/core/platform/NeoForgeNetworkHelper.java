package dev.riftal.creator.core.platform;

import dev.riftal.creator.core.net.ClientHandler;
import dev.riftal.creator.core.net.ServerHandler;
import dev.riftal.creator.core.platform.services.INetworkHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * NeoForge implementation of {@link INetworkHelper}.
 *
 * <p>Registrations are queued and flushed inside {@code RegisterPayloadHandlersEvent} on the mod
 * bus, which fires after mod construction.
 *
 * <p><b>Every send is filtered per player.</b> NeoForge validates clientbound custom payloads in
 * {@code NetworkRegistry#checkPacket} and <em>throws</em>
 * {@code UnsupportedOperationException: Payload [id] may not be sent to the client!} when the
 * receiving connection never negotiated our channel. That throw used to escape into feature code:
 * a player whose connection cannot carry the payload (one that is still mid-handshake, another
 * mod's fake player, a GameTest mock player) turned a routine HUD broadcast into an exception, and
 * when the caller was a scheduled task the task was cancelled for the rest of the server's life.
 * A client that cannot receive an update must simply not get it, so {@code canReceive} checks the
 * channel first and the broadcast helpers send per player rather than in one unfiltered sweep.
 */
public final class NeoForgeNetworkHelper implements INetworkHelper {

    private static final List<Consumer<PayloadRegistrar>> PENDING = new ArrayList<>();

    @Override
    public <T extends CustomPacketPayload> void registerC2S(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                            ServerHandler<T> handler) {
        PENDING.add(registrar -> registrar.playToServer(type, codec, (payload, context) -> {
            if (context.player() instanceof ServerPlayer sender) {
                handler.handle(payload, sender);
            }
        }));
    }

    @Override
    public <T extends CustomPacketPayload> void registerS2C(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                            ClientHandler<T> handler) {
        PENDING.add(registrar -> registrar.playToClient(type, codec,
                (payload, context) -> handler.handle(payload)));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (canReceive(player, payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    @Override
    public void sendToAll(MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (canReceive(player, payload)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    @Override
    public void sendToTracking(Entity entity, CustomPacketPayload payload) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        // NeoForge's ChunkMap#getPlayersWatching is the tracker's own seenBy set, so this reaches
        // exactly the players ServerChunkCache#broadcastAndSend would have reached - going through
        // the list by hand is what makes the per-player channel check possible.
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayersWatching(entity)) {
            if (player != entity && canReceive(player, payload)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
        if (entity instanceof ServerPlayer self && canReceive(self, payload)) {
            PacketDistributor.sendToPlayer(self, payload);
        }
    }

    /**
     * True when this player's connection exists and has an open channel for the payload. False for
     * a player that is not really on the network - {@code ServerPlayer#connection} is null until
     * the player list places them, and a connection that never ran NeoForge's channel negotiation
     * has no channel for a modded payload.
     */
    private static boolean canReceive(ServerPlayer player, CustomPacketPayload payload) {
        ServerGamePacketListenerImpl connection = player.connection;
        return connection != null && connection.hasChannel(payload.type());
    }

    /** Mod-bus hook wired up by {@code CreatorModsNeoForgeBootstrap}. */
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        for (Consumer<PayloadRegistrar> pending : PENDING) {
            pending.accept(registrar);
        }
    }
}
