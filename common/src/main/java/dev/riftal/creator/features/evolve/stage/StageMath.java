package dev.riftal.creator.features.evolve.stage;

/**
 * Every number the evolution ladder needs, as pure functions. No Minecraft types, so the whole file
 * is covered by plain JUnit.
 */
public final class StageMath {

    /** Evolution XP for killing a boss (dragon, wither, warden). */
    public static final int XP_BOSS = 200;

    /** Evolution XP for killing another player. */
    public static final int XP_PLAYER = 50;

    /** Evolution XP for killing a hostile mob. */
    public static final int XP_ENEMY = 15;

    /** Extra XP on top of {@link #XP_ENEMY} for a beefy hostile (40+ max health). */
    public static final int XP_ENEMY_TOUGH_BONUS = 5;

    /** Max health at which a hostile counts as beefy. */
    public static final float TOUGH_HEALTH = 40.0F;

    /** Evolution XP for killing anything else - animals, villagers, golems. */
    public static final int XP_OTHER = 3;

    /** Evolution XP per point of food nutrition. */
    public static final int XP_PER_NUTRITION = 2;

    /**
     * Highest stage whose threshold is at or below {@code totalXp}. Negative XP reads as stage 1.
     */
    public static int stageForXp(int totalXp) {
        int stage = Stages.MIN;
        for (int ordinal = Stages.MIN; ordinal <= Stages.MAX; ordinal++) {
            if (totalXp >= Stages.thresholdFor(ordinal)) {
                stage = ordinal;
            }
        }
        return stage;
    }

    /**
     * Progress from the given stage's threshold towards the next one, 0..1. Always 1 at Apex, and
     * never outside 0..1 for any input.
     */
    public static float progressFraction(int totalXp, int stageOrdinal) {
        int ordinal = Stages.clamp(stageOrdinal);
        if (ordinal >= Stages.MAX) {
            return 1.0F;
        }
        int from = Stages.thresholdFor(ordinal);
        int to = Stages.thresholdFor(ordinal + 1);
        int span = to - from;
        if (span <= 0) {
            return 1.0F;
        }
        float raw = (float) (totalXp - from) / (float) span;
        return raw < 0.0F ? 0.0F : Math.min(raw, 1.0F);
    }

    /** XP still needed before the next transformation, or 0 at Apex. */
    public static int xpToNextStage(int totalXp, int stageOrdinal) {
        int ordinal = Stages.clamp(stageOrdinal);
        if (ordinal >= Stages.MAX) {
            return 0;
        }
        return Math.max(0, Stages.thresholdFor(ordinal + 1) - totalXp);
    }

    /**
     * Evolution XP awarded for one kill.
     *
     * @param victimIsPlayer    the victim was another player
     * @param victimIsBoss      the victim was a dragon, wither or warden
     * @param victimIsEnemy     the victim implements vanilla's {@code Enemy} marker
     * @param victimMaxHealth   the victim's max health, used for the tough-mob bonus
     */
    public static int xpForKill(boolean victimIsPlayer, boolean victimIsBoss, boolean victimIsEnemy,
                                float victimMaxHealth) {
        if (victimIsBoss) {
            return XP_BOSS;
        }
        if (victimIsPlayer) {
            return XP_PLAYER;
        }
        if (victimIsEnemy) {
            return victimMaxHealth >= TOUGH_HEALTH ? XP_ENEMY + XP_ENEMY_TOUGH_BONUS : XP_ENEMY;
        }
        return XP_OTHER;
    }

    /** Evolution XP for finishing a meal of the given nutrition. */
    public static int xpForFood(int nutrition) {
        return Math.max(0, nutrition) * XP_PER_NUTRITION;
    }

    /** The XP a player must hold to sit exactly on {@code stageOrdinal}. */
    public static int xpFloorForStage(int stageOrdinal) {
        return Stages.thresholdFor(stageOrdinal);
    }

    private StageMath() {
    }
}
