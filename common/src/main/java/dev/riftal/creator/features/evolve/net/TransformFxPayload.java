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
 * client state. This payload drives the things that are genuinely per-viewer: the white flash (full
 * strength for the transforming player, falling off for witnesses within
 * {@code EvolutionHud.WITNESS_FLASH_RADIUS} blocks), the "locked" readout on the HUD, and the one
 * shot of {@code animation.apex.roar} the beast plays when it lands.
 *
 * <p>Sent to the subject <em>and</em> to everyone tracking them, so every one of those three uses
 * reads the tracker copy as well as the self copy.
 *
 * @param kind        {@link #START}, {@link #STOP} or {@link #ROAR}
 * @param ticks       how long the sequence lasts, from the moment the START is sent
 * @param targetStage the stage being transformed into; the HUD keeps showing {@code targetStage - 1}
 *                    until the STOP lands, so the title card gets to break the news
 */
public record TransformFxPayload(UUID playerId, int kind, int ticks, int targetStage)
        implements CustomPacketPayload {

    /** The sequence has just begun. */
    public static final int START = 0;

    /** The sequence has finished or was cancelled. */
    public static final int STOP = 1;

    /** One-shot: play the Apex roar animation on this player's beast. Carries no duration. */
    public static final int ROAR = 2;

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

    /** Convenience factory for the one-shot roar. */
    public static TransformFxPayload roar(UUID playerId, int targetStage) {
        return new TransformFxPayload(playerId, ROAR, 0, targetStage);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
