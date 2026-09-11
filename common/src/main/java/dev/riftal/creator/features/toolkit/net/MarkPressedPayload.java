package dev.riftal.creator.features.toolkit.net;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The mark key (default {@code M}) was pressed. Carries nothing: the server decides whether the
 * sender may mark and what the mark says, which is the only safe way to treat a C2S packet.
 */
public record MarkPressedPayload() implements CustomPacketPayload {

    /** The single instance; the payload has no state. */
    public static final MarkPressedPayload INSTANCE = new MarkPressedPayload();

    public static final Type<MarkPressedPayload> TYPE = Payloads.type(ToolkitFeature.NAMESPACE, "mark_pressed");

    public static final StreamCodec<RegistryFriendlyByteBuf, MarkPressedPayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
