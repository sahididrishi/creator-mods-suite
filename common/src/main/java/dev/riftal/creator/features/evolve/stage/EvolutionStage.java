package dev.riftal.creator.features.evolve.stage;

/**
 * One immutable rung of the evolution ladder.
 *
 * <p>All the "bonus" numbers are attribute modifier amounts, not final values. Scale, health, speed
 * and attack are {@code ADD_MULTIPLIED_TOTAL} (so armour, potions and weapons still stack on top);
 * step height, jump strength, reach and safe-fall are {@code ADD_VALUE} on the vanilla base.
 *
 * @param ordinal          1..5, also the stage's sort order and the number shown on the title card
 * @param key              lowercase id used in translation keys, e.g. {@code hatchling}
 * @param xpToReach        cumulative evolution XP needed to stand on this rung
 * @param scaleBonus       {@code minecraft:generic.scale}, multiplied-total (-0.4 gives 0.6x)
 * @param healthBonus      {@code minecraft:generic.max_health}, multiplied-total
 * @param speedBonus       {@code minecraft:generic.movement_speed}, multiplied-total
 * @param attackBonus      {@code minecraft:generic.attack_damage}, multiplied-total
 * @param stepBonus        {@code minecraft:generic.step_height}, added to the 0.6 base
 * @param jumpBonus        {@code minecraft:generic.jump_strength}, added to the 0.42 base
 * @param blockReachBonus  {@code minecraft:player.block_interaction_range}, added
 * @param entityReachBonus {@code minecraft:player.entity_interaction_range}, added
 * @param safeFallBonus    {@code minecraft:generic.safe_fall_distance}, added
 * @param perkKey          lowercase id of the passive granted at this stage
 * @param beastModel       true when the player renders as the Apex beast instead of their skin
 * @param colour           ARGB used for the HUD bar and the stage name
 */
public record EvolutionStage(
        int ordinal,
        String key,
        int xpToReach,
        double scaleBonus,
        double healthBonus,
        double speedBonus,
        double attackBonus,
        double stepBonus,
        double jumpBonus,
        double blockReachBonus,
        double entityReachBonus,
        double safeFallBonus,
        String perkKey,
        boolean beastModel,
        int colour) {

    /** Final multiplier applied to the player's size, i.e. {@code 1 + scaleBonus}. */
    public double scaleMultiplier() {
        return 1.0D + scaleBonus;
    }

    /** Translation key of the stage name, e.g. {@code stage.creator_evolve.brute}. */
    public String nameKey() {
        return "stage.creator_evolve." + key;
    }

    /** Translation key of this stage's passive, e.g. {@code perk.creator_evolve.charge}. */
    public String perkNameKey() {
        return "perk.creator_evolve." + perkKey;
    }

    /** True for the last rung, where there is nothing left to evolve into. */
    public boolean isFinal() {
        return ordinal >= Stages.MAX;
    }
}
