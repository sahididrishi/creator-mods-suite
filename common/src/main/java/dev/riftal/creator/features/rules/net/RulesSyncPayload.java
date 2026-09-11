package dev.riftal.creator.features.rules.net;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.rules.RulesFeature;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Server to client: the full ordered list of active rule ids plus whether the HUD list should be
 * drawn at all. Sent on every toggle, preset, clear and on join.
 *
 * <p>Only ids travel: the client turns them into names with {@code rule.creator_rules.<id>} out of
 * its own lang file, so nothing has to be translated server side.
 *
 * @param activeIds ordered rule ids
 * @param hud       false when {@code /rule hud off} has hidden the list for a clean frame
 */
public record RulesSyncPayload(List<String> activeIds, boolean hud) implements CustomPacketPayload {

    public static final Type<RulesSyncPayload> TYPE = Payloads.type(RulesFeature.NAMESPACE, "rules_sync");

    public static final StreamCodec<ByteBuf, RulesSyncPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)), RulesSyncPayload::activeIds,
                    ByteBufCodecs.BOOL, RulesSyncPayload::hud,
                    RulesSyncPayload::new);

    public RulesSyncPayload {
        activeIds = List.copyOf(activeIds);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
