package dev.riftal.creator.features.toolkit.net;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import dev.riftal.creator.features.toolkit.take.TakeState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Mirrors the server's take recorder onto every client so the HUD timer can run off the client's
 * own wall clock between updates.
 *
 * @param takeNumber    current take, 1..999
 * @param running       true between start and stop
 * @param startEpochMs  server wall clock when the take started
 * @param stoppedRtaMs  frozen duration once stopped
 * @param markCount     marks placed so far
 * @param serverEpochMs server wall clock right now, so the client can cancel clock skew
 */
public record TakeStatePayload(int takeNumber, boolean running, long startEpochMs,
                               long stoppedRtaMs, int markCount, long serverEpochMs)
        implements CustomPacketPayload {

    public static final Type<TakeStatePayload> TYPE = Payloads.type(ToolkitFeature.NAMESPACE, "take_state");

    public static final StreamCodec<RegistryFriendlyByteBuf, TakeStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TakeStatePayload::takeNumber,
            ByteBufCodecs.BOOL, TakeStatePayload::running,
            ByteBufCodecs.VAR_LONG, TakeStatePayload::startEpochMs,
            ByteBufCodecs.VAR_LONG, TakeStatePayload::stoppedRtaMs,
            ByteBufCodecs.VAR_INT, TakeStatePayload::markCount,
            ByteBufCodecs.VAR_LONG, TakeStatePayload::serverEpochMs,
            TakeStatePayload::new);

    /** Snapshot of {@code state}, stamped with the current server wall clock. */
    public static TakeStatePayload of(TakeState state, long nowEpochMs) {
        return new TakeStatePayload(state.number(), state.running(), state.startEpochMs(),
                state.rtaMillis(nowEpochMs), state.marks().size(), nowEpochMs);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
