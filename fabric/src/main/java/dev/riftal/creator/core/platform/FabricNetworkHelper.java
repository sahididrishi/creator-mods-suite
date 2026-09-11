package dev.riftal.creator.core.platform;

import dev.riftal.creator.core.net.ClientHandler;
import dev.riftal.creator.core.net.ServerHandler;
import dev.riftal.creator.core.platform.services.INetworkHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Fabric implementation of {@link INetworkHelper}. */
public final class FabricNetworkHelper implements INetworkHelper {

    private static boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public <T extends CustomPacketPayload> void registerC2S(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                            ServerHandler<T> handler) {
        PayloadTypeRegistry.playC2S().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type,
                (payload, context) -> handler.handle(payload, context.player()));
    }

    @Override
    public <T extends CustomPacketPayload> void registerS2C(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                            ClientHandler<T> handler) {
        PayloadTypeRegistry.playS2C().register(type, codec);
        if (isClient()) {
            // Separate class so ClientPlayNetworking is never loaded on a dedicated server.
            FabricClientNetwork.registerReceiver(type, handler);
        }
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        FabricClientNetwork.send(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        send(player, payload);
    }

    @Override
    public void sendToAll(MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, payload);
        }
    }

    @Override
    public void sendToTracking(Entity entity, CustomPacketPayload payload) {
        if (!(entity.level() instanceof ServerLevel)) {
            return;
        }
        for (ServerPlayer player : PlayerLookup.tracking(entity)) {
            send(player, payload);
        }
        if (entity instanceof ServerPlayer self) {
            send(self, payload);
        }
    }

    /**
     * Sends to one player, skipping any player that is not actually on the network.
     * {@code ServerPlayNetworking.send} dereferences {@code ServerPlayer#connection}, which is null
     * until the player list places the player - a connection-less player (another mod's fake
     * player, a GameTest stand-in, a player mid-disconnect) would otherwise turn an ordinary HUD
     * mirror into a NullPointerException inside whatever feature code happened to be running.
     */
    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection == null) {
            return;
        }
        ServerPlayNetworking.send(player, payload);
    }
}
