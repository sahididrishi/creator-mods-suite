package dev.riftal.creator.core.platform.services;

import dev.riftal.creator.core.net.ClientHandler;
import dev.riftal.creator.core.net.ServerHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Loader view of the play-phase custom payload APIs. Features never call this directly - use
 * {@code dev.riftal.creator.core.net.Payloads}.
 */
public interface INetworkHelper {

    <T extends CustomPacketPayload> void registerC2S(CustomPacketPayload.Type<T> type,
                                                     StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                     ServerHandler<T> handler);

    <T extends CustomPacketPayload> void registerS2C(CustomPacketPayload.Type<T> type,
                                                     StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                     ClientHandler<T> handler);

    void sendToServer(CustomPacketPayload payload);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    void sendToAll(MinecraftServer server, CustomPacketPayload payload);

    void sendToTracking(Entity entity, CustomPacketPayload payload);
}
