package dev.riftal.creator.features.powers.net;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.powers.PowersFeature;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Locale;

/**
 * Server to client: the recording controls for the HUD itself, driven by {@code /power hud}.
 *
 * <p>Separate from the ability traffic because it is a <em>presentation</em> switch the creator
 * flips between takes - hiding the row for a clean shot, or swapping the radial sweep for a linear
 * bar when the icons are on screen at a small GUI scale.
 */
public record PowerHudPayload(int mode) implements CustomPacketPayload {

    /** Hide the cooldown row entirely. */
    public static final int MODE_OFF = 0;
    /** Show the cooldown row. */
    public static final int MODE_ON = 1;
    /** Show, with the clockwise radial sweep. */
    public static final int MODE_RADIAL = 2;
    /** Show, with a bottom-up linear fill. */
    public static final int MODE_LINEAR = 3;

    public static final CustomPacketPayload.Type<PowerHudPayload> TYPE =
            Payloads.type(PowersFeature.NAMESPACE, "hud_mode");

    public static final StreamCodec<RegistryFriendlyByteBuf, PowerHudPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, PowerHudPayload::mode, PowerHudPayload::new);

    /** Parses the {@code /power hud <mode>} word, or {@code -1} when it is not one of the four. */
    public static int parse(String word) {
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "off" -> MODE_OFF;
            case "on" -> MODE_ON;
            case "radial" -> MODE_RADIAL;
            case "linear" -> MODE_LINEAR;
            default -> -1;
        };
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
