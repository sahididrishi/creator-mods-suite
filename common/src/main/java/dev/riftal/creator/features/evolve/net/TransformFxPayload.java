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
 * Server to client: start or stop the transformation screen effect for one player.
 *
 * <p>The spiral and the chime are spawned server-side so every nearby player sees them without any
 * client state. This payload only drives the things that are genuinely per-viewer: the white flash
 * and the "locked" readout on the HUD.
 *
 * @param kind        {@link #START} or {@link #STOP}
 * @param ticks       how long the sequence lasts, from the moment the START is sent
 * @param targetStage the stage being transformed into, used for the HUD colour
 */
public record TransformFxPayload(UUID playerId, int kind, int ticks, int targetStage)
        implements CustomPacketPayload {

    /** The sequence has just begun. */
    public static final int START = 0;

    /** The sequence has finished or was cancelled. */
    public static final int STOP = 1;

    public static final Type<TransformFxPayload> TYPE =
            Payloads.type(EvolveFeature.NAMESPACE, "transform_fx");

    public static final StreamCodec<RegistryFriendlyByteBuf, TransformFxPayload> CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, TransformFxPayload::playerId,
                    ByteBufCodecs.VAR_INT, TransformFxPayload::kind,
                    ByteBufCodecs.VAR_INT, TransformFxPayload::ticks,
                    ByteBufCodecs.VAR_INT, TransformFxPayload::targetStage,
                    TransformFxPayload::new);

    /** Convenience factory for the START packet. */
    public static TransformFxPayload start(UUID playerId, int ticks, int targetStage) {
        return new TransformFxPayload(playerId, START, ticks, targetStage);
    }

    /** Convenience factory for the STOP packet. */
    public static TransformFxPayload stop(UUID playerId, int targetStage) {
        return new TransformFxPayload(playerId, STOP, 0, targetStage);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
