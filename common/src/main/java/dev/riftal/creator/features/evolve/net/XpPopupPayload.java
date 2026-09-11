package dev.riftal.creator.features.evolve.net;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.evolve.EvolveFeature;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client, to the earning player only: the floating "+15 EVO" text.
 *
 * @param source one of {@link #SOURCE_KILL}, {@link #SOURCE_FOOD}, {@link #SOURCE_COMMAND}
 */
public record XpPopupPayload(int amount, int source) implements CustomPacketPayload {

    public static final int SOURCE_KILL = 0;
    public static final int SOURCE_FOOD = 1;
    public static final int SOURCE_COMMAND = 2;

    public static final Type<XpPopupPayload> TYPE =
            Payloads.type(EvolveFeature.NAMESPACE, "xp_popup");

    public static final StreamCodec<RegistryFriendlyByteBuf, XpPopupPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, XpPopupPayload::amount,
                    ByteBufCodecs.VAR_INT, XpPopupPayload::source,
                    XpPopupPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
