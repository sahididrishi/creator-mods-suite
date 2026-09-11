package dev.riftal.creator.features.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.rules.net.RuleToastPayload;
import dev.riftal.creator.features.rules.net.RulesSyncPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The two server-to-client payloads are the whole HUD contract: if the wire format is wrong, the
 * rule list on camera is wrong. Both codecs are built out of {@code ByteBufCodecs} primitives only,
 * so they round trip on a bare {@link ByteBuf} without any registry access.
 */
class RulePayloadCodecTest {

    private static RulesSyncPayload roundTrip(RulesSyncPayload payload) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            RulesSyncPayload.CODEC.encode(buffer, payload);
            return RulesSyncPayload.CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static RuleToastPayload roundTrip(RuleToastPayload payload) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            RuleToastPayload.CODEC.encode(buffer, payload);
            return RuleToastPayload.CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test
    void syncPayloadKeepsIdsInOrder() {
        RulesSyncPayload payload =
                new RulesSyncPayload(List.of("random_drops", "crafts_x10", "giant_mobs"), true);

        RulesSyncPayload decoded = roundTrip(payload);

        assertEquals(List.of("random_drops", "crafts_x10", "giant_mobs"), decoded.activeIds());
        assertTrue(decoded.hud());
        assertEquals(payload, decoded);
    }

    @Test
    void syncPayloadCarriesTheHudFlagBothWays() {
        assertFalse(roundTrip(new RulesSyncPayload(List.of("one_heart"), false)).hud());
        assertTrue(roundTrip(new RulesSyncPayload(List.of("one_heart"), true)).hud());
    }

    @Test
    void anEmptyRuleSetRoundTrips() {
        RulesSyncPayload decoded = roundTrip(new RulesSyncPayload(List.of(), true));

        assertTrue(decoded.activeIds().isEmpty());
    }

    @Test
    void everyShippedRuleIdFitsInOneSyncPacket() {
        // The codec caps the list at 256 entries; the feature ships eleven, and a pack that
        // registers more still has to fit.
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            ids.add("rule_" + i);
        }

        assertEquals(ids, roundTrip(new RulesSyncPayload(ids, true)).activeIds());
    }

    @Test
    void anOversizedRuleListIsRejectedRatherThanSilentlyTruncated() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 257; i++) {
            ids.add("rule_" + i);
        }

        assertThrows(RuntimeException.class, () -> roundTrip(new RulesSyncPayload(ids, true)));
    }

    @Test
    void theSyncPayloadIsDefensiveAboutItsList() {
        List<String> mutable = new ArrayList<>(List.of("one_heart"));
        RulesSyncPayload payload = new RulesSyncPayload(mutable, true);

        mutable.add("gravity_x3");

        assertEquals(List.of("one_heart"), payload.activeIds(),
                "the payload must snapshot the active list, not alias RuleManager's");
        assertThrows(UnsupportedOperationException.class, () -> payload.activeIds().add("nope"));
    }

    @Test
    void toastPayloadRoundTripsBothStates() {
        RuleToastPayload on = roundTrip(new RuleToastPayload("lava_floor", true));
        RuleToastPayload off = roundTrip(new RuleToastPayload("lava_floor", false));

        assertEquals("lava_floor", on.ruleId());
        assertTrue(on.enabled());
        assertEquals("lava_floor", off.ruleId());
        assertFalse(off.enabled());
    }

    @Test
    void payloadIdsLiveInThisFeaturesNamespace() {
        assertEquals("creator_rules", RulesSyncPayload.TYPE.id().getNamespace());
        assertEquals("rules_sync", RulesSyncPayload.TYPE.id().getPath());
        assertEquals("creator_rules", RuleToastPayload.TYPE.id().getNamespace());
        assertEquals("rule_toast", RuleToastPayload.TYPE.id().getPath());

        assertSame(RulesSyncPayload.TYPE, new RulesSyncPayload(List.of(), true).type());
        assertSame(RuleToastPayload.TYPE, new RuleToastPayload("x", true).type());
    }
}
