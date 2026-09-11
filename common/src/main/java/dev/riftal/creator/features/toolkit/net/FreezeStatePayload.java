package dev.riftal.creator.features.toolkit.net;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Tells every client whether mobs and/or players are currently frozen, so the HUD can show a
 * {@code FROZEN} tag next to the take timer.
 *
 * @param mobs    mob tick-cancel is active
 * @param players non-op players are movement locked
 */
public record FreezeStatePayload(boolean mobs, boolean players) implements CustomPacketPayload {

    public static final Type<FreezeStatePayload> TYPE = Payloads.type(ToolkitFeature.NAMESPACE, "freeze_state");

    public static final StreamCodec<RegistryFriendlyByteBuf, FreezeStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, FreezeStatePayload::mobs,
            ByteBufCodecs.BOOL, FreezeStatePayload::players,
            FreezeStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
