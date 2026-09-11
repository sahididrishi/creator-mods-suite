package dev.riftal.creator.core.platform;

import dev.riftal.creator.core.net.ClientHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client-only half of {@link FabricNetworkHelper}. Kept in its own class so that
 * {@code ClientPlayNetworking} is never class-loaded on a dedicated server.
 */
final class FabricClientNetwork {

    static <T extends CustomPacketPayload> void registerReceiver(CustomPacketPayload.Type<T> type,
                                                                 ClientHandler<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.handle(payload));
    }

    static void send(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    private FabricClientNetwork() {
    }
}
