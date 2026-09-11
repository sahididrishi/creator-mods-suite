package dev.riftal.creator.core.platform;

import dev.riftal.creator.core.net.ClientHandler;
import dev.riftal.creator.core.net.ServerHandler;
import dev.riftal.creator.core.platform.services.INetworkHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToAll(MinecraftServer server, CustomPacketPayload payload) {
        PacketDistributor.sendToAllPlayers(payload);
    }

    @Override
    public void sendToTracking(Entity entity, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }

    /** Mod-bus hook wired up by {@code CreatorModsNeoForgeBootstrap}. */
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        for (Consumer<PayloadRegistrar> pending : PENDING) {
            pending.accept(registrar);
        }
    }
}
