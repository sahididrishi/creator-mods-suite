package dev.riftal.creator.features.rules.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The seeded permutation behind {@code random_drops}: every drop source (a block id or an entity
 * type id) is pinned to exactly one item id for the lifetime of a world.
 *
 * <p>Both input lists are sorted before the shuffle, so the mapping depends only on the seed and
 * on the <em>set</em> of ids - not on registry iteration order, which differs between mod sets.
 * {@link Collections#shuffle(List, Random)} with a {@link Random} seeded from a long is specified
 * by the JDK, so the same world produces the same mapping on every launch and on every machine.
 *
 * <p>Pure Java on purpose: it is the one part of this feature worth unit-testing hard.
 */
public final class RandomDropsMapping {

    private final Map<String, String> mapping;

    private RandomDropsMapping(Map<String, String> mapping) {
        this.mapping = mapping;
    }

    /**
     * Builds the mapping.
     *
     * @param seed    world-derived seed
     * @param sources drop sources, e.g. {@code "block:minecraft:stone"}
     * @param targets item ids that are allowed to be handed out
     * @return a mapping; empty when either list is empty
     */
    public static RandomDropsMapping build(long seed, List<String> sources, List<String> targets) {
        if (sources.isEmpty() || targets.isEmpty()) {
            return new RandomDropsMapping(Map.of());
        }
        List<String> orderedSources = new ArrayList<>(sources);
        Collections.sort(orderedSources);

        List<String> shuffled = new ArrayList<>(targets);
        Collections.sort(shuffled);
        Collections.shuffle(shuffled, new Random(seed));

        Map<String, String> built = new HashMap<>(orderedSources.size() * 2);
        for (int i = 0; i < orderedSources.size(); i++) {
            built.put(orderedSources.get(i), shuffled.get(i % shuffled.size()));
        }
        return new RandomDropsMapping(Map.copyOf(built));
    }

    /** The item id this source drops, or {@code null} if the source is unknown. */
    public String targetFor(String source) {
        return mapping.get(source);
    }

    public int size() {
        return mapping.size();
    }

    /** Unmodifiable view, for tests and diagnostics. */
    public Map<String, String> asMap() {
        return mapping;
    }

    /** Key for a block source. */
    public static String blockKey(String blockId) {
        return "block:" + blockId;
    }

    /** Key for an entity-type source. */
    public static String entityKey(String entityTypeId) {
        return "entity:" + entityTypeId;
    }
}
