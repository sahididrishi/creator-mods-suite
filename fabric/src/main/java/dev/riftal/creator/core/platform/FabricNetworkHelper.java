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
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToAll(MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Override
    public void sendToTracking(Entity entity, CustomPacketPayload payload) {
        if (!(entity.level() instanceof ServerLevel)) {
            return;
        }
        for (ServerPlayer player : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(player, payload);
        }
        if (entity instanceof ServerPlayer self) {
            ServerPlayNetworking.send(self, payload);
        }
    }
}
