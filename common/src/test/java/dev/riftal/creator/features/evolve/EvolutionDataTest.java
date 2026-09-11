package dev.riftal.creator.features.evolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.riftal.creator.features.evolve.data.EvolutionData;
import dev.riftal.creator.features.evolve.stage.Stages;
import org.junit.jupiter.api.Test;

/** The attachment record: clamping, the {@code with*} copies and the codec. */
class EvolutionDataTest {

    @Test
    void initialIsHatchlingWithNothingEarned() {
        EvolutionData data = EvolutionData.initial();
        assertEquals(1, data.stage());
        assertEquals(0, data.xp());
        assertEquals(0, data.totalKills());
        assertFalse(data.transforming());
        assertEquals(EvolutionData.MODEL_AUTO, data.modelOverride());
    }

    @Test
    void theCanonicalConstructorClampsEveryField() {
        EvolutionData low = new EvolutionData(-4, -100, -1, false, -5L, -9);
        assertEquals(Stages.MIN, low.stage());
        assertEquals(0, low.xp());
        assertEquals(0, low.totalKills());
        assertEquals(0L, low.transformEndTick());
        assertEquals(EvolutionData.MODEL_FORCED_OFF, low.modelOverride());

        EvolutionData high = new EvolutionData(99, 10, 10, true, 10L, 9);
        assertEquals(Stages.MAX, high.stage());
        assertEquals(EvolutionData.MODEL_FORCED_ON, high.modelOverride());
    }

    @Test
    void atStagePinsXpToThatStageFloor() {
        EvolutionData data = EvolutionData.initial().withXp(4_000).atStage(3);
        assertEquals(3, data.stage());
        assertEquals(300, data.xp());
        assertEquals(0.0F, data.progress(), 1.0E-6F);
        assertEquals(400, data.xpToNext());
    }

    @Test
    void withCopiesLeaveEverythingElseAlone() {
        EvolutionData base = new EvolutionData(3, 412, 27, false, 0L, EvolutionData.MODEL_AUTO);

        assertEquals(412, base.withStage(4).xp());
        assertEquals(27, base.withXp(0).totalKills());
        assertEquals(3, base.withKills(99).stage());
        assertEquals(412, base.withModelOverride(EvolutionData.MODEL_FORCED_ON).xp());

        EvolutionData transforming = base.withTransform(true, 1234L);
        assertTrue(transforming.transforming());
        assertEquals(1234L, transforming.transformEndTick());
        assertEquals(3, transforming.stage());
    }

    @Test
    void beastModelFollowsTheStageUnlessOverridden() {
        EvolutionData apex = EvolutionData.initial().atStage(Stages.MAX);
        EvolutionData hatchling = EvolutionData.initial();

        assertTrue(apex.usesBeastModel());
        assertFalse(hatchling.usesBeastModel());

        assertFalse(apex.withModelOverride(EvolutionData.MODEL_FORCED_OFF).usesBeastModel());
        assertTrue(hatchling.withModelOverride(EvolutionData.MODEL_FORCED_ON).usesBeastModel());
    }

    @Test
    void progressAndXpToNextSaturateAtApex() {
        EvolutionData apex = new EvolutionData(5, 1500, 0, false, 0L, 0);
        assertEquals(1.0F, apex.progress(), 1.0E-6F);
        assertEquals(0, apex.xpToNext());
    }

    @Test
    void codecRoundTripsEveryField() {
        EvolutionData original = new EvolutionData(4, 912, 31, true, 55_555L,
                EvolutionData.MODEL_FORCED_OFF);

        JsonElement encoded = EvolutionData.CODEC
                .encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow();
        EvolutionData decoded = EvolutionData.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();

        assertEquals(original, decoded);
    }

    @Test
    void codecFillsInEveryMissingField() {
        JsonElement sparse = JsonParser.parseString("{}");
        EvolutionData decoded = EvolutionData.CODEC.parse(JsonOps.INSTANCE, sparse).getOrThrow();
        assertEquals(EvolutionData.INITIAL, decoded);
    }

    @Test
    void codecReadsALegacySaveThatOnlyHasStageAndXp() {
        JsonElement partial = JsonParser.parseString("{\"stage\":3,\"xp\":412}");
        EvolutionData decoded = EvolutionData.CODEC.parse(JsonOps.INSTANCE, partial).getOrThrow();
        assertEquals(3, decoded.stage());
        assertEquals(412, decoded.xp());
        assertEquals(0, decoded.totalKills());
        assertFalse(decoded.transforming());
    }
}
