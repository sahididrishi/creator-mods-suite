package dev.riftal.creator.features.powers.net;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.powers.PowersFeature;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: "I pressed the key bound to this ability".
 *
 * <p>Carries nothing but the id. Everything else - is it granted, is it off cooldown, is the player
 * even allowed to use it right now - is decided server side, because this packet is entirely
 * attacker controlled.
 */
public record UseAbilityPayload(ResourceLocation abilityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UseAbilityPayload> TYPE =
            Payloads.type(PowersFeature.NAMESPACE, "use_ability");

    public static final StreamCodec<RegistryFriendlyByteBuf, UseAbilityPayload> CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, UseAbilityPayload::abilityId,
                    UseAbilityPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
