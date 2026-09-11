package dev.riftal.creator.features.arsenal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.riftal.creator.features.arsenal.net.GrappleFxPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The hook effect packet. It is cosmetic, but a wrong wire format is a particle burst at the wrong
 * place - on camera that reads as a bug. The codec is built from {@code ByteBufCodecs} primitives
 * only, so it round trips on a bare {@link ByteBuf} with no registry access.
 */
class GrappleFxPayloadTest {

    private static GrappleFxPayload roundTrip(GrappleFxPayload payload) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            GrappleFxPayload.CODEC.encode(buffer, payload);
            return GrappleFxPayload.CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test
    void aBitePacketRoundTrips() {
        GrappleFxPayload payload =
                new GrappleFxPayload(4231, GrappleFxPayload.STATE_BITE, 12.5F, 70.25F, -308.75F);

        GrappleFxPayload decoded = roundTrip(payload);

        assertEquals(payload, decoded);
        assertEquals(4231, decoded.hookEntityId());
        assertEquals(GrappleFxPayload.STATE_BITE, decoded.state());
    }

    @Test
    void aReturnPacketRoundTripsWithNegativeCoordinates() {
        GrappleFxPayload decoded = roundTrip(
                new GrappleFxPayload(1, GrappleFxPayload.STATE_RETURN, -1024.5F, -64.0F, 2048.25F));

        assertEquals(GrappleFxPayload.STATE_RETURN, decoded.state());
        assertEquals(new Vec3(-1024.5D, -64.0D, 2048.25D), decoded.pos());
    }

    @Test
    void theCodecReadsExactlyWhatItWrote() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            GrappleFxPayload.CODEC.encode(buffer,
                    new GrappleFxPayload(7, GrappleFxPayload.STATE_BITE, 1.0F, 2.0F, 3.0F));
            GrappleFxPayload.CODEC.decode(buffer);

            assertEquals(0, buffer.readableBytes(), "the decoder left bytes on the buffer");
        } finally {
            buffer.release();
        }
    }

    @Test
    void positionIsExposedAsAVector() {
        GrappleFxPayload payload =
                new GrappleFxPayload(9, GrappleFxPayload.STATE_BITE, 1.5F, 2.5F, 3.5F);

        assertEquals(new Vec3(1.5D, 2.5D, 3.5D), payload.pos());
    }

    @Test
    void thePayloadIdLivesInThisFeaturesNamespace() {
        assertEquals("creator_arsenal", GrappleFxPayload.TYPE.id().getNamespace());
        assertEquals("grapple_fx", GrappleFxPayload.TYPE.id().getPath());
        assertSame(GrappleFxPayload.TYPE,
                new GrappleFxPayload(0, GrappleFxPayload.STATE_BITE, 0.0F, 0.0F, 0.0F).type());
    }

    @Test
    void theTwoStatesAreDistinctAndNonZero() {
        assertEquals(1, GrappleFxPayload.STATE_BITE);
        assertEquals(2, GrappleFxPayload.STATE_RETURN);
    }
}
