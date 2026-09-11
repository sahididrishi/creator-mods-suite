package dev.riftal.creator.features.events.siege;

import java.util.List;

/**
 * One wave of {@code /event start siege}: what to spawn, and how many of each.
 *
 * @param spawns entity id to count, in spawn order
 */
public record SiegeWave(List<Spawn> spawns) {

    /**
     * One kind of mob in a wave.
     *
     * @param entityId a registry id such as {@code minecraft:zombie}
     * @param count    how many at Normal difficulty, before scaling
     */
    public record Spawn(String entityId, int count) {
    }

    /** Total mobs in this wave once the difficulty factor is applied. */
    public int total(double difficultyFactor) {
        int sum = 0;
        for (Spawn spawn : spawns) {
            sum += SiegeWaves.scale(spawn.count(), difficultyFactor);
        }
        return sum;
    }
}
