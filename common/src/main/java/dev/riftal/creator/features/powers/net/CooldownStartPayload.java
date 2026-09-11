package dev.riftal.creator.features.powers.net;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.powers.PowersFeature;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: one slot's cooldown changed.
 *
 * <p>Sent when an ability fires (so the sweep starts on the exact server tick), and also when the
 * server <em>rejects</em> a use - in that case it carries the unchanged {@code readyAt}, which
 * silently corrects a client that had predicted wrong. No chat, no error sound, no spam.
 */
public record CooldownStartPayload(ResourceLocation abilityId, long startedAt, long readyAt, long serverGameTime)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CooldownStartPayload> TYPE =
            Payloads.type(PowersFeature.NAMESPACE, "cooldown_start");

    public static final StreamCodec<RegistryFriendlyByteBuf, CooldownStartPayload> CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, CooldownStartPayload::abilityId,
                    ByteBufCodecs.VAR_LONG, CooldownStartPayload::startedAt,
                    ByteBufCodecs.VAR_LONG, CooldownStartPayload::readyAt,
                    ByteBufCodecs.VAR_LONG, CooldownStartPayload::serverGameTime,
                    CooldownStartPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
