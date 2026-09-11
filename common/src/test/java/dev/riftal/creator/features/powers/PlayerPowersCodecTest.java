package dev.riftal.creator.features.powers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.riftal.creator.features.powers.data.PlayerPowers;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * The attachment codec. This is what is written into the player's {@code .dat}, so a change here
 * silently wipes everybody's loadout - hence the round trip and the "old save still loads" cases
 * (plan 03 section 9, unit tests 9 and 10).
 */
class PlayerPowersCodecTest {

    private static final ResourceLocation DASH =
            ResourceLocation.fromNamespaceAndPath(PowersFeature.NAMESPACE, "dash");
    private static final ResourceLocation DOME =
            ResourceLocation.fromNamespaceAndPath(PowersFeature.NAMESPACE, "shield_dome");

    private static PlayerPowers decode(String json) {
        JsonElement element = JsonParser.parseString(json);
        DataResult<Pair<PlayerPowers, JsonElement>> result =
                PlayerPowers.CODEC.decode(JsonOps.INSTANCE, element);
        return result.getOrThrow().getFirst();
    }

    @Test
    void roundTripsThroughTheCodec() {
        PlayerPowers original = PlayerPowers.EMPTY
                .grant(DASH).grant(DOME)
                .startCooldown(DASH, 1_000L, 60)
                .startCooldown(DOME, 1_000L, 400);

        JsonElement encoded = PlayerPowers.CODEC
                .encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow();
        PlayerPowers decoded = PlayerPowers.CODEC
                .decode(JsonOps.INSTANCE, encoded)
                .getOrThrow()
                .getFirst();

        assertEquals(original, decoded);
        assertEquals(List.of(DASH, DOME), decoded.granted(), "slot order has to survive the save");
        assertEquals(1_060L, decoded.readyAt().get(DASH).longValue());
    }

    @Test
    void anEmptyLoadoutRoundTrips() {
        JsonElement encoded = PlayerPowers.CODEC
                .encodeStart(JsonOps.INSTANCE, PlayerPowers.EMPTY)
                .getOrThrow();

        assertEquals(PlayerPowers.EMPTY, decode(encoded.toString()));
    }

    @Test
    void aSaveWithNoCooldownMapStillLoads() {
        PlayerPowers decoded = decode("{\"granted\":[\"creator_powers:dash\"]}");

        assertEquals(List.of(DASH), decoded.granted());
        assertTrue(decoded.readyAt().isEmpty());
        assertTrue(decoded.isReady(DASH, 0L));
    }

    @Test
    void aSaveWithNothingAtAllStillLoads() {
        // The optionalFieldOf pair is what stops a player who logged in before this feature existed
        // from getting a decode error on every join.
        assertEquals(PlayerPowers.EMPTY, decode("{}"));
    }

    @Test
    void cooldownsAreStoredUnderTheDocumentedKeys() {
        JsonElement encoded = PlayerPowers.CODEC
                .encodeStart(JsonOps.INSTANCE, PlayerPowers.EMPTY.grant(DASH).withReadyAt(DASH, 123L))
                .getOrThrow();

        String json = encoded.toString();
        assertTrue(json.contains("\"granted\""), json);
        assertTrue(json.contains("\"ready_at\""), json);
        assertTrue(json.contains("creator_powers:dash"), json);
    }

    @Test
    void aMalformedAbilityIdIsRejectedRatherThanSilentlyDropped() {
        DataResult<Pair<PlayerPowers, JsonElement>> result =
                PlayerPowers.CODEC.decode(JsonOps.INSTANCE,
                        JsonParser.parseString("{\"granted\":[\"NOT AN ID\"]}"));

        assertTrue(result.isError(), "a broken id must surface as a decode error, not an empty loadout");
    }

    @Test
    void readyAtKeysDecodeBackIntoResourceLocations() {
        PlayerPowers decoded = decode(
                "{\"granted\":[\"creator_powers:shield_dome\"],\"ready_at\":{\"creator_powers:shield_dome\":400}}");

        assertEquals(Map.of(DOME, 400L), decoded.readyAt());
        assertEquals(400, decoded.remaining(DOME, 0L));
    }
}
