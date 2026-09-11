package dev.riftal.creator.features.powers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.powers.data.PlayerPowers;
import dev.riftal.creator.features.powers.net.CooldownStartPayload;
import dev.riftal.creator.features.powers.net.PowerHudPayload;
import dev.riftal.creator.features.powers.net.SyncPowersPayload;
import dev.riftal.creator.features.powers.net.UseAbilityPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The four payloads: the HUD mirror built from a loadout, the wire round trip of each codec, and
 * the {@code /power hud} word parser (plan 03 section 9, unit tests 13-15).
 *
 * <p>Every codec here is built from {@code ResourceLocation.STREAM_CODEC} and
 * {@code ByteBufCodecs.VAR_LONG} only - neither consults the registry access - so an empty
 * {@link RegistryAccess} is enough and no game bootstrap is needed.
 */
class PowerPayloadTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PowersFeature.NAMESPACE, path);
    }

    private static final ResourceLocation DASH = id("dash");
    private static final ResourceLocation DOME = id("shield_dome");

    private static <T> T roundTrip(T payload,
                                   StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            codec.encode(buffer, payload);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test
    void useAbilityRoundTrips() {
        UseAbilityPayload decoded = roundTrip(new UseAbilityPayload(DASH), UseAbilityPayload.CODEC);

        assertEquals(DASH, decoded.abilityId());
        assertEquals("creator_powers:use_ability", UseAbilityPayload.TYPE.id().toString());
    }

    @Test
    void cooldownStartRoundTripsWithItsThreeClocks() {
        CooldownStartPayload decoded = roundTrip(
                new CooldownStartPayload(DOME, 1_000L, 1_400L, 1_000L, false), CooldownStartPayload.CODEC);

        assertEquals(DOME, decoded.abilityId());
        assertEquals(1_000L, decoded.startedAt());
        assertEquals(1_400L, decoded.readyAt());
        assertEquals(1_000L, decoded.serverGameTime());
        assertFalse(decoded.refused(), "an ability that actually fired is not a refusal");
    }

    /**
     * A refusal is what the HUD reads to shake the slot instead of chiming. It has to survive the
     * wire, and it has to be distinguishable from the zero-length window it shares its clocks with:
     * without the flag, {@code startedAt == readyAt == now} is exactly "this slot is ready", and the
     * client's ready edge-detector plays the chime at a player who was just told no.
     */
    @Test
    void aRefusalRoundTripsAndIsNotAZeroLengthCooldown() {
        CooldownStartPayload refused = roundTrip(
                new CooldownStartPayload(DASH, 900L, 900L, 900L, true), CooldownStartPayload.CODEC);

        assertTrue(refused.refused(), "the refusal marker must survive the wire");
        assertEquals(refused.startedAt(), refused.readyAt(),
                "a canUse refusal starts no cooldown at all");

        CooldownStartPayload ready = roundTrip(
                new CooldownStartPayload(DASH, 900L, 900L, 900L, false), CooldownStartPayload.CODEC);
        assertFalse(ready.refused());
        assertNotEquals(refused, ready,
                "the two carry identical clocks, so only the flag can tell them apart");
    }

    /** The correction for a press during a cooldown carries the real window, not a fresh one. */
    @Test
    void aCooldownRefusalKeepsTheOriginalWindow() {
        CooldownStartPayload decoded = roundTrip(
                new CooldownStartPayload(DOME, 1_000L, 1_400L, 1_100L, true), CooldownStartPayload.CODEC);

        assertTrue(decoded.refused());
        assertEquals(1_000L, decoded.startedAt(), "the window must not restart from the press");
        assertEquals(1_400L, decoded.readyAt());
        assertEquals(1_100L, decoded.serverGameTime());
    }

    @Test
    void syncRoundTripsEmptyAndFull() {
        assertEquals(new SyncPowersPayload(List.of(), List.of(), 0L),
                roundTrip(new SyncPowersPayload(List.of(), List.of(), 0L), SyncPowersPayload.CODEC));

        PlayerPowers powers = PlayerPowers.EMPTY;
        for (int i = 0; i < PlayerPowers.MAX_SLOTS; i++) {
            powers = powers.grant(id("slot_" + i)).startCooldown(id("slot_" + i), 500L, 20 * (i + 1));
        }
        SyncPowersPayload sent = SyncPowersPayload.of(powers, 500L);
        SyncPowersPayload decoded = roundTrip(sent, SyncPowersPayload.CODEC);

        assertEquals(sent, decoded);
        assertEquals(PlayerPowers.MAX_SLOTS, decoded.granted().size());
        assertEquals(520L, decoded.asCooldownMap().get(id("slot_0")).longValue());
    }

    @Test
    void hudModeRoundTrips() {
        assertEquals(PowerHudPayload.MODE_LINEAR,
                roundTrip(new PowerHudPayload(PowerHudPayload.MODE_LINEAR), PowerHudPayload.CODEC).mode());
    }

    @Test
    void theCodecsAreSymmetricForEveryAbilityId() {
        Function<ResourceLocation, ResourceLocation> wire =
                value -> roundTrip(new UseAbilityPayload(value), UseAbilityPayload.CODEC).abilityId();
        for (String path : List.of("dash", "fire_burst", "ground_pound",
                "ender_pull", "shield_dome", "mob_freeze")) {
            assertEquals(id(path), wire.apply(id(path)));
        }
    }

    // ------------------------------------------------------------------ sync payload

    @Test
    void syncCarriesTheSlotsInOrderWithOneReadyTickEach() {
        PlayerPowers powers = PlayerPowers.EMPTY
                .grant(DASH).grant(DOME)
                .startCooldown(DOME, 1_000L, 400);

        SyncPowersPayload payload = SyncPowersPayload.of(powers, 1_050L);

        assertEquals(List.of(DASH, DOME), payload.granted());
        assertEquals(List.of(1_050L, 1_400L), payload.readyAt(),
                "a ready ability reports 'now', so the client draws no sweep for it");
        assertEquals(1_050L, payload.serverGameTime());
    }

    @Test
    void syncOfAnEmptyLoadoutIsEmptyRatherThanNull() {
        SyncPowersPayload payload = SyncPowersPayload.of(PlayerPowers.EMPTY, 7L);

        assertTrue(payload.granted().isEmpty());
        assertTrue(payload.readyAt().isEmpty());
        assertTrue(payload.asCooldownMap().isEmpty());
    }

    @Test
    void theCooldownMapIsRebuiltFromTheParallelLists() {
        SyncPowersPayload payload = SyncPowersPayload.of(
                PlayerPowers.EMPTY.grant(DASH).grant(DOME).startCooldown(DASH, 0L, 60), 10L);

        assertEquals(Map.of(DASH, 60L, DOME, 10L), payload.asCooldownMap());
    }

    @Test
    void aTruncatedPacketIsToleratedInsteadOfThrowing() {
        // Never trust the wire: a mismatched pair of lists must degrade, not blow up the client tick.
        SyncPowersPayload payload = new SyncPowersPayload(List.of(DASH, DOME), List.of(99L), 0L);

        assertEquals(Map.of(DASH, 99L), payload.asCooldownMap());
    }

    @Test
    void thePayloadCopiesItsListsSoTheServerCannotMutateWhatWasSent() {
        SyncPowersPayload payload = SyncPowersPayload.of(PlayerPowers.EMPTY.grant(DASH), 0L);

        assertThrows(UnsupportedOperationException.class, () -> payload.granted().add(DOME));
        assertThrows(UnsupportedOperationException.class, () -> payload.readyAt().add(1L));
    }

    @Test
    void thePayloadIdsAreTheOnesTheProtocolDocuments() {
        assertEquals("creator_powers:sync_powers", SyncPowersPayload.TYPE.id().toString());
        assertEquals("creator_powers:hud_mode", PowerHudPayload.TYPE.id().toString());
    }

    @Test
    void aFullLoadoutStillFitsTheSixSlotListCodecLimit() {
        PlayerPowers powers = PlayerPowers.EMPTY;
        for (int i = 0; i < PlayerPowers.MAX_SLOTS; i++) {
            powers = powers.grant(id("slot_" + i));
        }
        SyncPowersPayload payload = SyncPowersPayload.of(powers, 0L);

        assertEquals(PlayerPowers.MAX_SLOTS, payload.granted().size());
        assertEquals(PlayerPowers.MAX_SLOTS, payload.readyAt().size());
    }

    // ------------------------------------------------------------------ /power hud

    @Test
    void hudModeWordsParseToTheDocumentedConstants() {
        assertEquals(PowerHudPayload.MODE_OFF, PowerHudPayload.parse("off"));
        assertEquals(PowerHudPayload.MODE_ON, PowerHudPayload.parse("on"));
        assertEquals(PowerHudPayload.MODE_RADIAL, PowerHudPayload.parse("radial"));
        assertEquals(PowerHudPayload.MODE_LINEAR, PowerHudPayload.parse("linear"));
    }

    @Test
    void hudModeParsingIsCaseInsensitive() {
        assertEquals(PowerHudPayload.MODE_RADIAL, PowerHudPayload.parse("RADIAL"));
        assertEquals(PowerHudPayload.MODE_LINEAR, PowerHudPayload.parse("Linear"));
    }

    @Test
    void anUnknownHudModeIsRejectedRatherThanDefaultingToOff() {
        assertEquals(-1, PowerHudPayload.parse("sideways"));
        assertEquals(-1, PowerHudPayload.parse(""));
    }
}
