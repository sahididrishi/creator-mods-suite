package dev.riftal.creator.features.evolve.net;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.evolve.EvolveFeature;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Server to client: one player's evolution state, sent to that player and to everyone tracking
 * them. The client HUD and the stage-5 model swap both read from this.
 */
public record SyncEvolutionPayload(UUID playerId, int stage, int xp, boolean transforming,
                                   int modelOverride) implements CustomPacketPayload {

    public static final Type<SyncEvolutionPayload> TYPE =
            Payloads.type(EvolveFeature.NAMESPACE, "sync");

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncEvolutionPayload> CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, SyncEvolutionPayload::playerId,
                    ByteBufCodecs.VAR_INT, SyncEvolutionPayload::stage,
                    ByteBufCodecs.VAR_INT, SyncEvolutionPayload::xp,
                    ByteBufCodecs.BOOL, SyncEvolutionPayload::transforming,
                    ByteBufCodecs.VAR_INT, SyncEvolutionPayload::modelOverride,
                    SyncEvolutionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
