package dev.riftal.creator.features.toolkit.net;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "I just joined a world - what is the take and freeze state?". Sent once per client world load,
 * which is how a player who logs in mid-take gets a correct HUD without the server having to hook
 * a join event.
 */
public record RequestSyncPayload() implements CustomPacketPayload {

    /** The single instance; the payload has no state. */
    public static final RequestSyncPayload INSTANCE = new RequestSyncPayload();

    public static final Type<RequestSyncPayload> TYPE = Payloads.type(ToolkitFeature.NAMESPACE, "request_sync");

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSyncPayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
