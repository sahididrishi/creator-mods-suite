package dev.riftal.creator.features.evolve.stage;

import java.util.List;

/**
 * The five-rung ladder. Pure data - no Minecraft classes, so it unit-tests without a game.
 *
 * <p>Balance comes straight from {@code plans/05-evolve.md} section 5. The resulting sizes are
 * 0.60x, 0.85x, 1.25x, 1.80x and 2.60x of a normal player.
 */
public final class Stages {

    /** Lowest stage ordinal. */
    public static final int MIN = 1;

    /** Highest stage ordinal. */
    public static final int MAX = 5;

    //                                        ord  key           xp   scale  health  speed  attack  step  jump  blkR  entR  fall  perk          beast  colour
    public static final EvolutionStage HATCHLING = new EvolutionStage(
            1, "hatchling", 0, -0.40D, -0.20D, 0.15D, -0.25D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, "burrow", false, 0xFF7FE06A);

    public static final EvolutionStage RUNT = new EvolutionStage(
            2, "runt", 100, -0.15D, 0.0D, 0.10D, 0.0D, 0.0D, 0.05D, 0.0D, 0.0D, 0.0D, "thick_skin", false, 0xFF9BE3C4);

    public static final EvolutionStage BRUTE = new EvolutionStage(
            3, "brute", 300, 0.25D, 0.30D, 0.05D, 0.50D, 0.40D, 0.08D, 0.0D, 0.0D, 0.0D, "charge", false, 0xFFE8C76A);

    public static final EvolutionStage TITAN = new EvolutionStage(
            4, "titan", 700, 0.80D, 0.75D, 0.0D, 1.20D, 1.40D, 0.20D, 1.50D, 2.0D, 2.0D, "stomp", false, 0xFFE8853F);

    public static final EvolutionStage APEX = new EvolutionStage(
            5, "apex", 1500, 1.60D, 1.50D, -0.10D, 2.0D, 1.40D, 0.25D, 1.50D, 2.0D, 2.0D, "roar", true, 0xFFC061E8);

    /** Every stage, ordinal order. Index 0 is Hatchling. */
    public static final List<EvolutionStage> ALL = List.of(HATCHLING, RUNT, BRUTE, TITAN, APEX);

    /** Cumulative XP needed to reach each stage, ordinal order. */
    public static final int[] THRESHOLDS = {0, 100, 300, 700, 1500};

    /** The stage with this ordinal; out-of-range ordinals clamp to the ends. */
    public static EvolutionStage byOrdinal(int ordinal) {
        return ALL.get(clamp(ordinal) - 1);
    }

    /** The stage after this one, or {@code null} at Apex. */
    public static EvolutionStage next(EvolutionStage stage) {
        return stage.ordinal() >= MAX ? null : byOrdinal(stage.ordinal() + 1);
    }

    /** Cumulative XP needed to reach {@code ordinal}. */
    public static int thresholdFor(int ordinal) {
        return THRESHOLDS[clamp(ordinal) - 1];
    }

    /** {@code ordinal} forced into 1..5. */
    public static int clamp(int ordinal) {
        return ordinal < MIN ? MIN : Math.min(ordinal, MAX);
    }

    private Stages() {
    }
}
