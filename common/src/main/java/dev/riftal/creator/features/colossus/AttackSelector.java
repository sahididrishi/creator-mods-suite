package dev.riftal.creator.features.colossus;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Picks the Ashen Colossus' next attack. Pure logic - no level, no entity, no registries - so the
 * fight's tuning can be unit-tested without booting the game.
 *
 * <p>Rules, in order:
 * <ol>
 *   <li>the summon is a <em>schedule</em>, not a candidate: once it is due it wins outright
 *       ({@link #summonIsDue});</li>
 *   <li>an attack whose phase gate is not met is never a candidate;</li>
 *   <li>an attack on cooldown is never a candidate;</li>
 *   <li>the attack that was used last is dropped whenever another candidate exists, so the boss
 *       never repeats itself back to back;</li>
 *   <li>what is left is picked by weight, with {@link #LAVA_RAIN_FAR_WEIGHT} making a distant
 *       target overwhelmingly likely to eat a volley rather than a slam.</li>
 * </ol>
 */
public final class AttackSelector {

    /** Slam only reaches this far (squared blocks). */
    public static final double SLAM_RANGE_SQ = 49.0D;

    /** The combo needs the target in melee (squared blocks). */
    public static final double COMBO_RANGE_SQ = 25.0D;

    /** Beyond this (squared blocks) the boss prefers to rain fire. */
    public static final double FAR_RANGE_SQ = 36.0D;

    /** Never more than this many minions alive at once. */
    public static final int MINION_CAP = 6;

    static final int SLAM_WEIGHT = 10;
    static final int LAVA_RAIN_NEAR_WEIGHT = 6;
    static final int LAVA_RAIN_FAR_WEIGHT = 40;
    static final int COMBO_WEIGHT = 14;

    /** Remaining cooldown ticks per attack. Immutable. */
    public record Cooldowns(int slam, int lavaRain, int summon, int combo) {

        public static final Cooldowns READY = new Cooldowns(0, 0, 0, 0);

        /** Remaining ticks for one kind; unknown kinds are always ready. */
        public int remaining(AttackKind kind) {
            return switch (kind) {
                case SLAM -> slam;
                case LAVA_RAIN -> lavaRain;
                case SUMMON -> summon;
                case COMBO -> combo;
                default -> 0;
            };
        }

        public boolean ready(AttackKind kind) {
            return remaining(kind) <= 0;
        }
    }

    /**
     * @param phase        the phase the boss is in right now
     * @param distSq       squared distance to the current target
     * @param cooldowns    remaining cooldown ticks
     * @param minionsAlive how many of the boss' minions are still alive
     * @param history      attack history, most recent last; may be null or empty
     * @param random       the boss' {@code RandomSource}; seeding it makes this deterministic
     * @return the attack to start, or {@link AttackKind#NONE} when nothing is available
     */
    public static AttackKind choose(BossPhase phase, double distSq, Cooldowns cooldowns,
                                    int minionsAlive, Deque<AttackKind> history, RandomSource random) {
        return choose(phase, distSq, cooldowns, minionsAlive,
                history == null ? null : history.peekLast(), random);
    }

    /** As {@link #choose(BossPhase, double, Cooldowns, int, Deque, RandomSource)}, given only the last pick. */
    public static AttackKind choose(BossPhase phase, double distSq, Cooldowns cooldowns,
                                    int minionsAlive, AttackKind last, RandomSource random) {
        if (summonIsDue(phase, cooldowns, minionsAlive)) {
            return AttackKind.SUMMON;
        }

        List<AttackKind> kinds = new ArrayList<>(4);
        List<Integer> weights = new ArrayList<>(4);

        if (cooldowns.ready(AttackKind.SLAM) && distSq <= SLAM_RANGE_SQ) {
            kinds.add(AttackKind.SLAM);
            weights.add(SLAM_WEIGHT);
        }
        if (phase.index() >= BossPhase.P2.index() && cooldowns.ready(AttackKind.LAVA_RAIN)) {
            kinds.add(AttackKind.LAVA_RAIN);
            weights.add(distSq > FAR_RANGE_SQ ? LAVA_RAIN_FAR_WEIGHT : LAVA_RAIN_NEAR_WEIGHT);
        }
        if (phase == BossPhase.P3 && cooldowns.ready(AttackKind.COMBO) && distSq <= COMBO_RANGE_SQ) {
            kinds.add(AttackKind.COMBO);
            weights.add(COMBO_WEIGHT);
        }

        if (kinds.isEmpty()) {
            return AttackKind.NONE;
        }
        if (kinds.size() > 1 && last != null) {
            int repeated = kinds.indexOf(last);
            if (repeated >= 0) {
                kinds.remove(repeated);
                weights.remove(repeated);
            }
        }

        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        if (total <= 0) {
            return kinds.get(0);
        }

        int roll = random.nextInt(total);
        for (int i = 0; i < kinds.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) {
                return kinds.get(i);
            }
        }
        return kinds.get(kinds.size() - 1);
    }

    /**
     * True when the summon is <em>owed</em>: phase 2 or later, its 20-second timer has run out and
     * there is room under the minion cap.
     *
     * <p>The plan's MVP is "Summon 3 Ashen Minions every 20 s (cap 6 alive)" - a schedule, not a
     * roll. As one weighted candidate among several it was neither: it used to carry a weight of 12
     * against a lava-rain weight of 40 at the plan's own shooting distance, so the demo's "three
     * minions rise at 0:20" beat could land several attack cycles late and could not be relied on
     * for a take. So the summon is not weighted at all: when it is due it simply is the next
     * attack, and the weighted pick decides only what the boss does in between.
     */
    public static boolean summonIsDue(BossPhase phase, Cooldowns cooldowns, int minionsAlive) {
        return phase.index() >= BossPhase.P2.index()
                && cooldowns.ready(AttackKind.SUMMON)
                && minionsAlive < MINION_CAP;
    }

    private AttackSelector() {
    }
}
