package dev.riftal.creator.features.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.rules.rules.RandomDropsMapping;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The determinism guarantee behind {@code random_drops}: same world, same mapping, forever. */
class RandomDropsMappingTest {

    private static List<String> ids(String prefix, int count) {
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(prefix + i);
        }
        return ids;
    }

    @Test
    void sameSeedGivesTheSameMapping() {
        List<String> sources = ids("block:s", 500);
        List<String> targets = ids("minecraft:item_", 500);

        RandomDropsMapping first = RandomDropsMapping.build(42L, sources, targets);
        RandomDropsMapping second = RandomDropsMapping.build(42L, sources, targets);

        assertEquals(first.asMap(), second.asMap());
    }

    @Test
    void sourceOrderDoesNotAffectTheMapping() {
        List<String> sources = ids("block:s", 200);
        List<String> shuffledSources = new ArrayList<>(sources);
        java.util.Collections.reverse(shuffledSources);
        List<String> targets = ids("minecraft:item_", 200);

        assertEquals(RandomDropsMapping.build(7L, sources, targets).asMap(),
                RandomDropsMapping.build(7L, shuffledSources, targets).asMap());
    }

    @Test
    void differentSeedsGiveMostlyDifferentMappings() {
        List<String> sources = ids("block:s", 500);
        List<String> targets = ids("minecraft:item_", 500);

        RandomDropsMapping a = RandomDropsMapping.build(42L, sources, targets);
        RandomDropsMapping b = RandomDropsMapping.build(43L, sources, targets);

        int same = 0;
        for (String source : sources) {
            if (a.targetFor(source).equals(b.targetFor(source))) {
                same++;
            }
        }
        assertTrue(same < sources.size() / 10,
                "seed 43 should differ from seed 42 for the vast majority of sources, matched " + same);
    }

    @Test
    void isABijectionWhenTheSizesMatch() {
        List<String> sources = ids("block:s", 64);
        List<String> targets = ids("minecraft:item_", 64);

        RandomDropsMapping mapping = RandomDropsMapping.build(1234L, sources, targets);

        Set<String> used = new HashSet<>(mapping.asMap().values());
        assertEquals(64, mapping.size());
        assertEquals(64, used.size(), "every item should be used exactly once");
    }

    @Test
    void blacklistedTargetsAreNeverUsed() {
        List<String> sources = ids("block:s", 30);
        List<String> targets = new ArrayList<>(ids("minecraft:item_", 10));

        RandomDropsMapping mapping = RandomDropsMapping.build(9L, sources, targets);

        for (String source : sources) {
            assertNotNull(mapping.targetFor(source));
            assertFalse(mapping.targetFor(source).equals("minecraft:bedrock"));
            assertTrue(targets.contains(mapping.targetFor(source)));
        }
    }

    @Test
    void emptyInputsGiveAnEmptyMapping() {
        assertEquals(0, RandomDropsMapping.build(1L, List.of(), List.of("minecraft:stone")).size());
        assertEquals(0, RandomDropsMapping.build(1L, List.of("block:x"), List.of()).size());
    }

    @Test
    void keysAreNamespacedBySourceKind() {
        assertEquals("block:minecraft:stone", RandomDropsMapping.blockKey("minecraft:stone"));
        assertEquals("entity:minecraft:zombie", RandomDropsMapping.entityKey("minecraft:zombie"));
    }
}
