package dev.riftal.creator.features.rules.net;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.rules.RulesFeature;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: one rule just changed state. The HUD flashes that row for 40 ticks and clicks,
 * which is what makes the toggle read on camera without any chat noise.
 *
 * @param ruleId  the rule that changed
 * @param enabled true when it was switched on
 */
public record RuleToastPayload(String ruleId, boolean enabled) implements CustomPacketPayload {

    public static final Type<RuleToastPayload> TYPE = Payloads.type(RulesFeature.NAMESPACE, "rule_toast");

    public static final StreamCodec<ByteBuf, RuleToastPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, RuleToastPayload::ruleId,
                    ByteBufCodecs.BOOL, RuleToastPayload::enabled,
                    RuleToastPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
