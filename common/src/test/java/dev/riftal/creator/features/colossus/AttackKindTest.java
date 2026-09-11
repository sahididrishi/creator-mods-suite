package dev.riftal.creator.features.colossus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link AttackKind} is the fight's timeline. A hit tick that falls outside its own clip is damage
 * with no animation behind it - invisible on camera and impossible to spot in a play test, so it
 * is pinned here instead.
 */
class AttackKindTest {

    @Test
    void everyHitTickLandsInsideItsOwnClip() {
        for (AttackKind kind : AttackKind.values()) {
            for (int i = 0; i < kind.hitCount(); i++) {
                int tick = kind.hitTick(i);
                assertTrue(tick > 0, kind + " hit " + i + " must be after the clip starts");
                assertTrue(tick < kind.durationTicks(),
                        kind + " hit " + i + " at " + tick + " is outside its "
                                + kind.durationTicks() + "-tick clip");
            }
        }
    }

    @Test
    void hitTicksAreStrictlyAscending() {
        for (AttackKind kind : AttackKind.values()) {
            int[] ticks = kind.hitTicks();
            for (int i = 1; i < ticks.length; i++) {
                assertTrue(ticks[i] > ticks[i - 1],
                        kind + " hit ticks must ascend: " + ticks[i - 1] + " then " + ticks[i]);
            }
        }
    }

    @Test
    void hitTicksAreDefensivelyCopied() {
        int[] first = AttackKind.COMBO.hitTicks();
        first[0] = 999;
        assertArrayEquals(new int[]{10, 22, 38}, AttackKind.COMBO.hitTicks());
        assertNotSame(first, AttackKind.COMBO.hitTicks());
    }

    @Test
    void comboLandsThreeHitsOverTwoAndAHalfSeconds() {
        assertEquals(3, AttackKind.COMBO.hitCount());
        assertEquals(50, AttackKind.COMBO.durationTicks());
        assertArrayEquals(new int[]{10, 22, 38}, AttackKind.COMBO.hitTicks());
    }

    @Test
    void slamImpactIsHalfwayThroughTheClip() {
        assertEquals(40, AttackKind.SLAM.durationTicks());
        assertEquals(20, AttackKind.SLAM.hitTick(0));
    }

    @Test
    void deathClipIsSeventyTicks() {
        assertEquals(70, AttackKind.DEATH.durationTicks());
        assertEquals(0, AttackKind.DEATH.hitCount());
    }

    @Test
    void onlySpawnAndRoarAreInvulnerable() {
        Set<AttackKind> invulnerable = EnumSet.noneOf(AttackKind.class);
        for (AttackKind kind : AttackKind.values()) {
            if (kind.isInvulnerable()) {
                invulnerable.add(kind);
            }
        }
        assertEquals(EnumSet.of(AttackKind.SPAWN, AttackKind.ROAR), invulnerable);
    }

    @Test
    void theChooserOnlyEverSeesTheFourRealAttacks() {
        Set<AttackKind> choosable = EnumSet.noneOf(AttackKind.class);
        for (AttackKind kind : AttackKind.values()) {
            if (kind.isChoosable()) {
                choosable.add(kind);
            }
        }
        assertEquals(
                EnumSet.of(AttackKind.SLAM, AttackKind.LAVA_RAIN, AttackKind.SUMMON, AttackKind.COMBO),
                choosable);
    }

    @Test
    void everyChoosableAttackHasACooldown() {
        for (AttackKind kind : AttackKind.values()) {
            if (kind.isChoosable()) {
                assertTrue(kind.cooldownTicks() > 0, kind + " needs its own cooldown");
            }
        }
    }

    @Test
    void idsRoundTripThroughSynchedData() {
        for (AttackKind kind : AttackKind.values()) {
            assertSame(kind, AttackKind.byId(kind.id()), kind.name());
        }
        assertSame(AttackKind.NONE, AttackKind.byId(-1));
        assertSame(AttackKind.NONE, AttackKind.byId(AttackKind.values().length));
    }

    @Test
    void animationNamesAreDistinctAndSnakeCase() {
        Set<String> seen = new java.util.HashSet<>();
        for (AttackKind kind : AttackKind.values()) {
            String name = kind.animName();
            assertTrue(seen.add(name), "duplicate animation name " + name);
            assertEquals(name.toLowerCase(java.util.Locale.ROOT), name, name + " must be lower case");
            assertFalse(name.isBlank(), kind + " has no animation name");
        }
    }

    /** The NBT form of the attack history, which survives a relog. */
    @Test
    void namesRoundTripThroughNbt() {
        for (AttackKind kind : AttackKind.values()) {
            assertSame(kind, AttackKind.byName(kind.name()), kind.name());
        }
        // A tag written by another build must load as "no history", not blow up the world load.
        assertSame(AttackKind.NONE, AttackKind.byName("SOMETHING_ELSE"));
        assertSame(AttackKind.NONE, AttackKind.byName(""));
    }

    @Test
    void idleHasNoTimelineAtAll() {
        assertEquals(0, AttackKind.NONE.durationTicks());
        assertEquals(0, AttackKind.NONE.hitCount());
        assertEquals(0, AttackKind.NONE.cooldownTicks());
        assertFalse(AttackKind.NONE.isChoosable());
    }
}
