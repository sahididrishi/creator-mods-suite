package dev.riftal.creator.features.colossus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.colossus.AttackSelector.Cooldowns;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/**
 * The fight's tuning, tested without booting the game.
 *
 * <p>Every assertion here is a thing a viewer would notice: the boss using a phase 2 move in
 * phase 1, the same attack twice in a row, a summon past the minion cap, or a distant player
 * being slammed from seven blocks away.
 */
class AttackSelectorTest {

    private static final double NEAR_SQ = 9.0D;    // 3 blocks: inside slam and combo range
    private static final double FAR_SQ = 100.0D;   // 10 blocks: outside both melee ranges

    private static RandomSource seeded() {
        return RandomSource.create(20260911L);
    }

    private static Set<AttackKind> picks(BossPhase phase, double distSq, Cooldowns cooldowns,
                                         int minionsAlive, int samples) {
        RandomSource random = seeded();
        Set<AttackKind> seen = EnumSet.noneOf(AttackKind.class);
        for (int i = 0; i < samples; i++) {
            seen.add(AttackSelector.choose(phase, distSq, cooldowns, minionsAlive,
                    (AttackKind) null, random));
        }
        return seen;
    }

    private static Map<AttackKind, Integer> histogram(BossPhase phase, double distSq,
                                                      Cooldowns cooldowns, int minionsAlive,
                                                      int samples) {
        RandomSource random = seeded();
        Map<AttackKind, Integer> counts = new EnumMap<>(AttackKind.class);
        for (int i = 0; i < samples; i++) {
            AttackKind pick = AttackSelector.choose(phase, distSq, cooldowns, minionsAlive,
                    (AttackKind) null, random);
            counts.merge(pick, 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void phaseOneOnlyEverSlams() {
        assertEquals(EnumSet.of(AttackKind.SLAM),
                picks(BossPhase.P1, NEAR_SQ, Cooldowns.READY, 0, 10_000));
    }

    @Test
    void phaseOneWithAnOutOfRangeTargetDoesNothing() {
        // Slam is the only phase 1 move and it cannot reach: the boss walks instead of
        // swinging at thin air.
        assertSame(AttackKind.NONE, AttackSelector.choose(BossPhase.P1, FAR_SQ, Cooldowns.READY,
                0, (AttackKind) null, seeded()));
    }

    @Test
    void phaseTwoAddsLavaRainAndSummonButNeverTheCombo() {
        Set<AttackKind> seen = picks(BossPhase.P2, NEAR_SQ, Cooldowns.READY, 0, 10_000);
        assertEquals(EnumSet.of(AttackKind.SLAM, AttackKind.LAVA_RAIN, AttackKind.SUMMON), seen);
        assertFalse(seen.contains(AttackKind.COMBO));
    }

    @Test
    void phaseThreeAddsTheCombo() {
        Set<AttackKind> seen = picks(BossPhase.P3, NEAR_SQ, Cooldowns.READY, 0, 10_000);
        assertTrue(seen.contains(AttackKind.COMBO), "phase 3 must be able to combo");
        assertEquals(EnumSet.of(AttackKind.SLAM, AttackKind.LAVA_RAIN, AttackKind.SUMMON,
                AttackKind.COMBO), seen);
    }

    @Test
    void theComboNeedsTheTargetInMelee() {
        // 6 blocks: still inside slam range (7) but outside combo range (5).
        double betweenSq = 36.0D;
        assertTrue(betweenSq <= AttackSelector.SLAM_RANGE_SQ);
        assertTrue(betweenSq > AttackSelector.COMBO_RANGE_SQ);
        assertFalse(picks(BossPhase.P3, betweenSq, Cooldowns.READY, 0, 5_000)
                .contains(AttackKind.COMBO));
    }

    @Test
    void aDistantTargetIsRainedOnRatherThanChased() {
        Map<AttackKind, Integer> counts = histogram(BossPhase.P2, FAR_SQ, Cooldowns.READY, 0, 10_000);
        assertFalse(counts.containsKey(AttackKind.SLAM), "slam cannot reach 10 blocks");
        int lava = counts.getOrDefault(AttackKind.LAVA_RAIN, 0);
        assertTrue(lava > 6_000, "lava rain should dominate at range, got " + lava + "/10000");
    }

    @Test
    void summonStopsAtTheMinionCap() {
        assertFalse(picks(BossPhase.P2, NEAR_SQ, Cooldowns.READY, AttackSelector.MINION_CAP, 5_000)
                .contains(AttackKind.SUMMON));
        assertTrue(picks(BossPhase.P2, NEAR_SQ, Cooldowns.READY, AttackSelector.MINION_CAP - 1, 5_000)
                .contains(AttackKind.SUMMON));
    }

    @Test
    void anAttackOnCooldownIsNeverPicked() {
        Cooldowns slamCooling = new Cooldowns(37, 0, 0, 0);
        assertSame(AttackKind.NONE, AttackSelector.choose(BossPhase.P1, NEAR_SQ, slamCooling,
                0, (AttackKind) null, seeded()));
        assertFalse(picks(BossPhase.P2, NEAR_SQ, slamCooling, 0, 5_000).contains(AttackKind.SLAM));
    }

    @Test
    void cooldownsRecordAndReadBackPerKind() {
        Cooldowns cooldowns = new Cooldowns(1, 2, 3, 4);
        assertEquals(1, cooldowns.remaining(AttackKind.SLAM));
        assertEquals(2, cooldowns.remaining(AttackKind.LAVA_RAIN));
        assertEquals(3, cooldowns.remaining(AttackKind.SUMMON));
        assertEquals(4, cooldowns.remaining(AttackKind.COMBO));
        assertEquals(0, cooldowns.remaining(AttackKind.ROAR));
        assertFalse(cooldowns.ready(AttackKind.SLAM));
        assertTrue(cooldowns.ready(AttackKind.ROAR));
        assertTrue(Cooldowns.READY.ready(AttackKind.COMBO));
    }

    @Test
    void theSameAttackNeverRunsTwiceInARow() {
        RandomSource random = seeded();
        for (int i = 0; i < 5_000; i++) {
            AttackKind pick = AttackSelector.choose(BossPhase.P2, NEAR_SQ, Cooldowns.READY, 0,
                    AttackKind.SLAM, random);
            assertFalse(pick == AttackKind.SLAM,
                    "slam repeated even though lava rain and summon were available");
            assertFalse(pick == AttackKind.NONE, "something should still have been available");
        }
    }

    @Test
    void theHistoryOverloadReadsTheMostRecentPick() {
        Deque<AttackKind> history = new ArrayDeque<>();
        history.addLast(AttackKind.LAVA_RAIN);
        history.addLast(AttackKind.SLAM);   // most recent
        RandomSource random = seeded();
        for (int i = 0; i < 2_000; i++) {
            assertFalse(AttackSelector.choose(BossPhase.P2, NEAR_SQ, Cooldowns.READY, 0,
                    history, random) == AttackKind.SLAM);
        }
    }

    @Test
    void anEmptyHistoryIsNotTreatedAsARepeat() {
        assertSame(AttackKind.SLAM, AttackSelector.choose(BossPhase.P1, NEAR_SQ, Cooldowns.READY,
                0, new ArrayDeque<>(), seeded()));
        assertSame(AttackKind.SLAM, AttackSelector.choose(BossPhase.P1, NEAR_SQ, Cooldowns.READY,
                0, (Deque<AttackKind>) null, seeded()));
    }

    @Test
    void aSoleSurvivorRepeatsRatherThanStalling() {
        // Phase 1 has one move. Refusing to repeat it would leave the boss standing still.
        assertSame(AttackKind.SLAM, AttackSelector.choose(BossPhase.P1, NEAR_SQ, Cooldowns.READY,
                0, AttackKind.SLAM, seeded()));
    }

    @Test
    void everythingOnCooldownYieldsNone() {
        Cooldowns all = new Cooldowns(10, 10, 10, 10);
        assertSame(AttackKind.NONE, AttackSelector.choose(BossPhase.P3, NEAR_SQ, all, 0,
                (AttackKind) null, seeded()));
    }

    @Test
    void theSameSeedProducesTheSameHundredPicks() {
        List<AttackKind> first = new ArrayList<>();
        List<AttackKind> second = new ArrayList<>();
        RandomSource a = seeded();
        RandomSource b = seeded();
        for (int i = 0; i < 100; i++) {
            first.add(AttackSelector.choose(BossPhase.P3, NEAR_SQ, Cooldowns.READY, 0,
                    (AttackKind) null, a));
            second.add(AttackSelector.choose(BossPhase.P3, NEAR_SQ, Cooldowns.READY, 0,
                    (AttackKind) null, b));
        }
        assertEquals(first, second);
        assertTrue(first.stream().distinct().count() > 1, "a seeded run should still vary");
    }

    @Test
    void everyPickIsAnAttackTheGoalsCanActuallyRun() {
        for (AttackKind pick : picks(BossPhase.P3, NEAR_SQ, Cooldowns.READY, 0, 10_000)) {
            assertTrue(pick.isChoosable(), pick + " has no goal behind it");
        }
    }
}
