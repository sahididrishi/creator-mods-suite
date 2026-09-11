package dev.riftal.creator.features.evolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.evolve.net.SyncEvolutionPayload;
import dev.riftal.creator.features.evolve.net.TransformFxPayload;
import dev.riftal.creator.features.evolve.net.XpPopupPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * The three server-to-client payloads: stream codecs round-trip byte for byte, and every id sits in
 * this feature's namespace.
 *
 * <p>No registry access is needed - all four fields of every payload are primitives or a UUID - so
 * {@link RegistryAccess#EMPTY} is enough of a registry view for the buffer.
 */
class EvolvePayloadCodecTest {

    private static final UUID PLAYER = UUID.fromString("2f9c1d3a-4b5e-4c7a-8d9e-0f1a2b3c4d5e");

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test
    void syncPayloadRoundTripsAndConsumesEveryByte() {
        SyncEvolutionPayload original = new SyncEvolutionPayload(PLAYER, 4, 912, true, -1);

        RegistryFriendlyByteBuf buf = buffer();
        SyncEvolutionPayload.CODEC.encode(buf, original);
        int written = buf.readableBytes();
        SyncEvolutionPayload decoded = SyncEvolutionPayload.CODEC.decode(buf);

        assertEquals(original, decoded);
        assertTrue(written > 0, "the sync payload should write something");
        assertEquals(0, buf.readableBytes(), "the decoder must consume exactly what the encoder wrote");
    }

    @Test
    void transformFxPayloadRoundTrips() {
        TransformFxPayload start = TransformFxPayload.start(PLAYER, 60, 5);

        RegistryFriendlyByteBuf buf = buffer();
        TransformFxPayload.CODEC.encode(buf, start);
        TransformFxPayload decoded = TransformFxPayload.CODEC.decode(buf);

        assertEquals(start, decoded);
        assertEquals(TransformFxPayload.START, decoded.kind());
        assertEquals(60, decoded.ticks());
        assertEquals(5, decoded.targetStage());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void transformFxFactoriesDifferOnlyInKind() {
        TransformFxPayload start = TransformFxPayload.start(PLAYER, 40, 3);
        TransformFxPayload stop = TransformFxPayload.stop(PLAYER, 3);

        assertEquals(TransformFxPayload.START, start.kind());
        assertEquals(TransformFxPayload.STOP, stop.kind());
        assertNotEquals(start.kind(), stop.kind());
        assertEquals(0, stop.ticks(), "a stop packet carries no duration");
        assertEquals(start.playerId(), stop.playerId());
        assertEquals(start.targetStage(), stop.targetStage());
    }

    @Test
    void theRoarFactoryRoundTripsAndIsItsOwnKind() {
        TransformFxPayload roar = TransformFxPayload.roar(PLAYER, 5);

        assertEquals(TransformFxPayload.ROAR, roar.kind());
        assertNotEquals(TransformFxPayload.START, roar.kind());
        assertNotEquals(TransformFxPayload.STOP, roar.kind());
        assertEquals(0, roar.ticks(), "a roar packet carries no duration");
        assertEquals(5, roar.targetStage());

        RegistryFriendlyByteBuf buf = buffer();
        TransformFxPayload.CODEC.encode(buf, roar);
        TransformFxPayload decoded = TransformFxPayload.CODEC.decode(buf);

        assertEquals(roar, decoded);
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void xpPopupRoundTripsIncludingNegativeAmounts() {
        for (int amount : new int[] {15, 200, -250, 0}) {
            XpPopupPayload original = new XpPopupPayload(amount, XpPopupPayload.SOURCE_COMMAND);

            RegistryFriendlyByteBuf buf = buffer();
            XpPopupPayload.CODEC.encode(buf, original);
            XpPopupPayload decoded = XpPopupPayload.CODEC.decode(buf);

            assertEquals(original, decoded, "round trip failed for amount " + amount);
            assertEquals(0, buf.readableBytes());
        }
    }

    @Test
    void everyPayloadIdIsOursAndDistinct() {
        ResourceLocation sync = SyncEvolutionPayload.TYPE.id();
        ResourceLocation fx = TransformFxPayload.TYPE.id();
        ResourceLocation popup = XpPopupPayload.TYPE.id();

        for (ResourceLocation id : new ResourceLocation[] {sync, fx, popup}) {
            assertEquals(EvolveFeature.NAMESPACE, id.getNamespace(), id + " is in the wrong namespace");
        }
        assertEquals("sync", sync.getPath());
        assertEquals("transform_fx", fx.getPath());
        assertEquals("xp_popup", popup.getPath());
        assertNotEquals(sync, fx);
        assertNotEquals(fx, popup);
    }

    @Test
    void theThreeXpSourcesAreDistinct() {
        assertNotEquals(XpPopupPayload.SOURCE_KILL, XpPopupPayload.SOURCE_FOOD);
        assertNotEquals(XpPopupPayload.SOURCE_FOOD, XpPopupPayload.SOURCE_COMMAND);
        assertNotEquals(XpPopupPayload.SOURCE_KILL, XpPopupPayload.SOURCE_COMMAND);
    }
}
