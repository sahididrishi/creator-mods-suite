package dev.riftal.creator.features.toolkit.net;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The three client-side "clean frame" switches. Sent to exactly one player - the one who ran
 * {@code /toolkit hide ...} - so an op can never blank another crew member's screen by accident.
 *
 * @param hud      vanilla HUD off (same switch F1 uses)
 * @param chat     chat log off
 * @param nametags entity name tags off
 */
public record HideStatePayload(boolean hud, boolean chat, boolean nametags) implements CustomPacketPayload {

    public static final Type<HideStatePayload> TYPE = Payloads.type(ToolkitFeature.NAMESPACE, "hide_state");

    public static final StreamCodec<RegistryFriendlyByteBuf, HideStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, HideStatePayload::hud,
            ByteBufCodecs.BOOL, HideStatePayload::chat,
            ByteBufCodecs.BOOL, HideStatePayload::nametags,
            HideStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
