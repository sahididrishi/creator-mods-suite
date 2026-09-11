package dev.riftal.creator.features.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.toolkit.net.FreezeStatePayload;
import dev.riftal.creator.features.toolkit.net.HideStatePayload;
import dev.riftal.creator.features.toolkit.net.MarkPressedPayload;
import dev.riftal.creator.features.toolkit.net.RequestSyncPayload;
import dev.riftal.creator.features.toolkit.net.TakeStatePayload;
import dev.riftal.creator.features.toolkit.take.Mark;
import dev.riftal.creator.features.toolkit.take.TakeState;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The HUD mirror goes over the wire on every take change, so the five payloads have to round-trip
 * byte for byte and stay inside this feature's namespace.
 *
 * <p>No registry access is needed - every field is a primitive - so {@link RegistryAccess#EMPTY} is
 * enough of a registry view for the buffer.
 */
class ToolkitPayloadCodecTest {

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test
    void takeStateRoundTripsAndConsumesEveryByte() {
        TakeStatePayload original =
                new TakeStatePayload(42, true, 1_700_000_000_000L, 41_700L, 3, 1_700_000_041_700L);

        RegistryFriendlyByteBuf buf = buffer();
        TakeStatePayload.CODEC.encode(buf, original);
        int written = buf.readableBytes();
        TakeStatePayload decoded = TakeStatePayload.CODEC.decode(buf);

        assertEquals(original, decoded);
        assertTrue(written > 0, "the take state should write something");
        assertEquals(0, buf.readableBytes(), "the decoder must consume exactly what the encoder wrote");
    }

    @Test
    void takeStateSnapshotMatchesTheRecorder() {
        Mark one = new Mark(1, 1_500L, 1_700_000_001_500L, 1690, "Creator", "");
        TakeState running = new TakeState(7, true, 1_700_000_000_000L, 1660, 0L, List.of(one));

        TakeStatePayload payload = TakeStatePayload.of(running, 1_700_000_012_300L);

        assertEquals(7, payload.takeNumber());
        assertTrue(payload.running());
        assertEquals(1_700_000_000_000L, payload.startEpochMs());
        // While running, the payload carries the live duration and the server's clock, so the HUD
        // can subtract the two and keep counting locally.
        assertEquals(12_300L, payload.stoppedRtaMs());
        assertEquals(1, payload.markCount());
        assertEquals(1_700_000_012_300L, payload.serverEpochMs());
    }

    @Test
    void freezeAndHideStatesRoundTripEveryFlagCombination() {
        for (int bits = 0; bits < 8; bits++) {
            HideStatePayload hide = new HideStatePayload((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0);
            RegistryFriendlyByteBuf hideBuf = buffer();
            HideStatePayload.CODEC.encode(hideBuf, hide);
            assertEquals(hide, HideStatePayload.CODEC.decode(hideBuf));
            assertEquals(0, hideBuf.readableBytes());
        }
        for (int bits = 0; bits < 4; bits++) {
            FreezeStatePayload freeze = new FreezeStatePayload((bits & 1) != 0, (bits & 2) != 0);
            RegistryFriendlyByteBuf freezeBuf = buffer();
            FreezeStatePayload.CODEC.encode(freezeBuf, freeze);
            assertEquals(freeze, FreezeStatePayload.CODEC.decode(freezeBuf));
            assertEquals(0, freezeBuf.readableBytes());
        }
    }

    @Test
    void theTwoTriggerPayloadsCarryNothing() {
        RegistryFriendlyByteBuf buf = buffer();
        MarkPressedPayload.CODEC.encode(buf, MarkPressedPayload.INSTANCE);
        assertEquals(0, buf.readableBytes(), "a mark press must not be trusted with any data");
        assertSame(MarkPressedPayload.INSTANCE, MarkPressedPayload.CODEC.decode(buf));

        RegistryFriendlyByteBuf syncBuf = buffer();
        RequestSyncPayload.CODEC.encode(syncBuf, RequestSyncPayload.INSTANCE);
        assertEquals(0, syncBuf.readableBytes());
        assertSame(RequestSyncPayload.INSTANCE, RequestSyncPayload.CODEC.decode(syncBuf));
    }

    @Test
    void everyPayloadIdLivesInThisFeaturesNamespace() {
        for (CustomPacketPayload.Type<?> type : List.of(TakeStatePayload.TYPE, FreezeStatePayload.TYPE,
                HideStatePayload.TYPE, MarkPressedPayload.TYPE, RequestSyncPayload.TYPE)) {
            assertEquals(ToolkitFeature.NAMESPACE, type.id().getNamespace(), type.id().toString());
        }
        assertEquals("take_state", TakeStatePayload.TYPE.id().getPath());
        assertEquals(TakeStatePayload.TYPE, new TakeStatePayload(1, false, 0L, 0L, 0, 0L).type());
    }
}
