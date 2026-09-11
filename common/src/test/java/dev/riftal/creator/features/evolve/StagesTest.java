package dev.riftal.creator.features.evolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.evolve.stage.EvolutionStage;
import dev.riftal.creator.features.evolve.stage.StageModifiers;
import dev.riftal.creator.features.evolve.stage.Stages;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

/** The stage table itself: five rungs, rising thresholds, the sizes the plan promises. */
class StagesTest {

    @Test
    void thereAreExactlyFiveStagesInOrdinalOrder() {
        assertEquals(5, Stages.ALL.size());
        for (int i = 0; i < Stages.ALL.size(); i++) {
            assertEquals(i + 1, Stages.ALL.get(i).ordinal());
        }
    }

    @Test
    void thresholdsRiseStrictly() {
        for (int ordinal = Stages.MIN; ordinal < Stages.MAX; ordinal++) {
            assertTrue(Stages.thresholdFor(ordinal) < Stages.thresholdFor(ordinal + 1),
                    "threshold " + ordinal + " must be below " + (ordinal + 1));
        }
        assertEquals(0, Stages.thresholdFor(Stages.MIN));
    }

    @Test
    void stageKeysAndPerkKeysAreUnique() {
        Set<String> stageKeys = new HashSet<>();
        Set<String> perkKeys = new HashSet<>();
        for (EvolutionStage stage : Stages.ALL) {
            assertTrue(stageKeys.add(stage.key()), "duplicate stage key " + stage.key());
            assertTrue(perkKeys.add(stage.perkKey()), "duplicate perk key " + stage.perkKey());
            assertTrue(stage.key().matches("[a-z_]+"), stage.key() + " is not a legal key");
            assertEquals("stage.creator_evolve." + stage.key(), stage.nameKey());
            assertEquals("perk.creator_evolve." + stage.perkKey(), stage.perkNameKey());
        }
    }

    @Test
    void scaleModifiersProduceThePlannedSizes() {
        assertEquals(0.60D, Stages.HATCHLING.scaleMultiplier(), 1.0E-6D);
        assertEquals(0.85D, Stages.RUNT.scaleMultiplier(), 1.0E-6D);
        assertEquals(1.25D, Stages.BRUTE.scaleMultiplier(), 1.0E-6D);
        assertEquals(1.80D, Stages.TITAN.scaleMultiplier(), 1.0E-6D);
        assertEquals(2.60D, Stages.APEX.scaleMultiplier(), 1.0E-6D);
    }

    @Test
    void healthModifiersProduceThePlannedMaxima() {
        // Player base max health is 20; every stage uses ADD_MULTIPLIED_TOTAL.
        assertEquals(16.0D, 20.0D * (1.0D + Stages.HATCHLING.healthBonus()), 1.0E-6D);
        assertEquals(20.0D, 20.0D * (1.0D + Stages.RUNT.healthBonus()), 1.0E-6D);
        assertEquals(26.0D, 20.0D * (1.0D + Stages.BRUTE.healthBonus()), 1.0E-6D);
        assertEquals(35.0D, 20.0D * (1.0D + Stages.TITAN.healthBonus()), 1.0E-6D);
        assertEquals(50.0D, 20.0D * (1.0D + Stages.APEX.healthBonus()), 1.0E-6D);
    }

    @Test
    void onlyApexWearsTheBeast() {
        for (EvolutionStage stage : Stages.ALL) {
            assertEquals(stage.ordinal() == Stages.MAX, stage.beastModel(),
                    stage.key() + " beastModel flag is wrong");
        }
    }

    @Test
    void byOrdinalClampsInsteadOfThrowing() {
        assertEquals(Stages.HATCHLING, Stages.byOrdinal(0));
        assertEquals(Stages.HATCHLING, Stages.byOrdinal(-99));
        assertEquals(Stages.APEX, Stages.byOrdinal(6));
        assertEquals(Stages.APEX, Stages.byOrdinal(Integer.MAX_VALUE));
    }

    @Test
    void nextWalksTheLadderAndStopsAtApex() {
        assertEquals(Stages.RUNT, Stages.next(Stages.HATCHLING));
        assertNotNull(Stages.next(Stages.TITAN));
        assertNull(Stages.next(Stages.APEX));
    }

    @Test
    void everyModifierIdIsDistinctAndLivesInOurNamespace() {
        Set<ResourceLocation> seen = new HashSet<>();
        for (ResourceLocation id : StageModifiers.ALL_IDS) {
            assertTrue(seen.add(id), "duplicate modifier id " + id);
            assertEquals("creator_evolve", id.getNamespace());
        }
        assertEquals(10, StageModifiers.ALL_IDS.size());
        assertEquals(9, StageModifiers.STAGE_IDS.size());
        assertTrue(StageModifiers.ALL_IDS.containsAll(StageModifiers.STAGE_IDS));
        assertTrue(!StageModifiers.STAGE_IDS.contains(StageModifiers.TRANSFORM_LOCK_ID),
                "the transformation lock must not be treated as a stage modifier");
    }
}
