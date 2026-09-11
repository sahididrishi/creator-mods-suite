package dev.riftal.creator.features.colossus.gametest;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.colossus.ArenaRing;
import dev.riftal.creator.features.colossus.AttackKind;
import dev.riftal.creator.features.colossus.AttackSelector;
import dev.riftal.creator.features.colossus.BossPhase;
import dev.riftal.creator.features.colossus.ColossusFeature;
import dev.riftal.creator.features.colossus.Shockwave;
import dev.riftal.creator.features.colossus.entity.AshenColossusEntity;
import dev.riftal.creator.features.colossus.entity.AshenMinionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * GameTest bodies for the {@code colossus} feature. Vanilla API only, so both loaders can call them.
 *
 * <p>Add a {@code public static void name(GameTestHelper helper)} here, then one annotated stub in
 * {@code fabric/src/gametest/java/.../ColossusFabricGameTests.java} and one in
 * {@code neoforge/src/main/java/.../ColossusNeoForgeGameTests.java}. A sibling class would be
 * silently never discovered on Fabric - see CONTRACT.md section 11.2.
 *
 * <p>Two templates are available: {@code creator_colossus:empty} (9x9x9, shipped by the scaffold)
 * and {@code creator_colossus:arena_24} (24x12x24, written by
 * {@code features/colossus/tools/make_arena_structure.py}). Anything involving the shockwave, the
 * ring or minions needs the big one - the slam alone reaches seven blocks.
 *
 * <p>Most tests spawn the boss with {@code spawnWithNoFreeWill}, which strips its goals. That is
 * deliberate: every behaviour asserted here is driven straight through the entity's own public API,
 * so the tests are deterministic instead of waiting on a weighted attack roll and a pathfind.
 */
public final class ColossusGameTests {

    /** Middle of the {@code arena_24} floor. */
    private static final BlockPos ARENA_CENTRE = new BlockPos(12, 1, 12);

    /** Middle of the 9x9 {@code empty} floor. */
    private static final BlockPos SMALL_CENTRE = new BlockPos(4, 1, 4);

    /** Smoke test: the feature survived the config filter and is live in this session. */
    public static void featureIsEnabled(GameTestHelper helper) {
        helper.assertTrue(CreatorMods.isEnabled(ColossusFeature.ID),
                "feature '" + ColossusFeature.ID + "' should be enabled in the test session");
        helper.succeed();
    }

    // ------------------------------------------------------------------ phases and the boss bar

    /** A freshly spawned boss is at full health, in phase 1, with a full bar. */
    public static void spawnsAtFullHealthInPhaseOne(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, SMALL_CENTRE);

        helper.assertValueEqual(boss.getPhase(), BossPhase.P1.index(), "phase");
        helper.assertTrue(boss.getHealth() == boss.getMaxHealth(),
                "a new boss should be at full health, was " + boss.getHealth());
        helper.assertValueEqual(boss.getMaxHealth(), 600.0F, "max health");
        helper.assertTrue(boss.bossEvent().getColor() == BossEvent.BossBarColor.YELLOW,
                "the phase 1 bar is yellow, was " + boss.bossEvent().getColor());

        // Progress is pushed every other tick from tick(), not from the AI step, so it keeps
        // updating even for this goal-less boss - and while the AI is frozen for a take.
        helper.succeedOnTickWhen(6, () -> helper.assertTrue(
                boss.bossEvent().getProgress() == 1.0F,
                "boss bar should be full, was " + boss.bossEvent().getProgress()));
    }

    /** Damage alone drives the phase machine: the bar recolours and phase 3 darkens the screen. */
    public static void phaseThresholdsFollowHealth(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, SMALL_CENTRE);

        helper.runAtTickTime(2L, () -> boss.setHealth(boss.getMaxHealth() * 0.5F));
        helper.runAtTickTime(8L, () -> {
            helper.assertValueEqual(boss.getPhase(), BossPhase.P2.index(), "phase at 50% health");
            helper.assertTrue(boss.bossEvent().getColor() == BossEvent.BossBarColor.RED,
                    "the phase 2 bar is red, was " + boss.bossEvent().getColor());
            helper.assertFalse(boss.bossEvent().shouldDarkenScreen(),
                    "the screen only darkens in phase 3");
            boss.setHealth(boss.getMaxHealth() * 0.2F);
        });
        helper.succeedOnTickWhen(16, () -> {
            helper.assertValueEqual(boss.getPhase(), BossPhase.P3.index(), "phase at 20% health");
            helper.assertTrue(boss.bossEvent().getColor() == BossEvent.BossBarColor.PURPLE,
                    "the phase 3 bar is purple, was " + boss.bossEvent().getColor());
            helper.assertTrue(boss.bossEvent().shouldDarkenScreen(),
                    "phase 3 should darken the screen");
        });
    }

    /** Healing the boss must not walk the fight back to an earlier phase mid-take. */
    public static void phasesNeverRunBackwardsOnHealing(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, SMALL_CENTRE);

        helper.runAtTickTime(2L, () -> boss.setHealth(boss.getMaxHealth() * 0.2F));
        helper.runAtTickTime(10L, () -> {
            helper.assertValueEqual(boss.getPhase(), BossPhase.P3.index(), "phase at 20% health");
            boss.setHealth(boss.getMaxHealth());
        });
        helper.succeedOnTickWhen(20, () -> helper.assertValueEqual(
                boss.getPhase(), BossPhase.P3.index(), "phase after healing back to full"));
    }

    /** Phase 3 hangs the enrage speed modifier on the boss exactly once. */
    public static void phaseThreeEnragesTheBoss(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, SMALL_CENTRE);
        double base = boss.getAttributeValue(Attributes.MOVEMENT_SPEED);

        helper.runAtTickTime(2L, () -> {
            boss.forcePhase(BossPhase.P3);
            // Adding the same AttributeModifier id twice throws in 1.21 - re-entering the phase
            // must be a no-op, which is what /colossus phase 3 does on a second take.
            boss.forcePhase(BossPhase.P3);
        });
        helper.succeedOnTickWhen(10, () -> {
            double enraged = boss.getAttributeValue(Attributes.MOVEMENT_SPEED);
            helper.assertTrue(Math.abs(enraged - base * 1.5D) < 1.0E-6D,
                    "phase 3 is +50% movement speed: expected " + (base * 1.5D) + ", got " + enraged);
        });
    }

    /** Dropping back out of phase 3 with {@code /colossus phase 1} takes the speed bonus with it. */
    public static void leavingPhaseThreeRemovesTheEnrage(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, SMALL_CENTRE);
        double base = boss.getAttributeValue(Attributes.MOVEMENT_SPEED);

        helper.runAtTickTime(2L, () -> boss.forcePhase(BossPhase.P3));
        helper.runAtTickTime(8L, () -> boss.forcePhase(BossPhase.P1));
        helper.succeedOnTickWhen(16, () -> {
            double now = boss.getAttributeValue(Attributes.MOVEMENT_SPEED);
            helper.assertTrue(Math.abs(now - base) < 1.0E-6D,
                    "the enrage modifier should be gone: expected " + base + ", got " + now);
        });
    }

    /** The boss bar follows tracking, and goes away with the boss instead of sticking on screen. */
    public static void bossBarTracksSeenPlayers(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, SMALL_CENTRE);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            boss.startSeenByPlayer(player);
            helper.assertTrue(boss.bossEvent().getPlayers().contains(player),
                    "a player who starts tracking the boss should get the bar");

            boss.stopSeenByPlayer(player);
            helper.assertFalse(boss.bossEvent().getPlayers().contains(player),
                    "the bar should go when the boss stops being tracked");

            boss.startSeenByPlayer(player);
            boss.remove(Entity.RemovalReason.DISCARDED);
            helper.assertTrue(boss.bossEvent().getPlayers().isEmpty(),
                    "removing the boss must clear the bar for everyone, or it sticks on screen");
        } finally {
            // The mock player is placed in the real player list; leave the server as we found it
            // so the other features' tests are not sharing a world with a ghost.
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ the slam

    /** The shockwave hits what is standing in its band and throws it outward and up. */
    public static void slamDamagesAndLaunchesADummyInTheRing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);
        // An iron golem, not a pig: the slam does 13.5 damage and a pig has 10 hit points, so a
        // pig would die and take its delta movement with it.
        IronGolem dummy = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, ARENA_CENTRE.offset(0, 0, 3));
        float startHealth = dummy.getHealth();
        Set<UUID> alreadyHit = new HashSet<>();

        helper.runAtTickTime(4L, () -> {
            for (int tick = 1; tick <= Shockwave.EXPANSION_TICKS; tick++) {
                boss.emitSlamRing(level, tick, alreadyHit);
            }
            // Asserted in the same tick the ring lands: gravity would eat the upward kick within
            // a couple of ticks and the test would read as a false negative.
            helper.assertTrue(dummy.getDeltaMovement().y > 0.0D,
                    "the shockwave throws victims up as well as out, dy was "
                            + dummy.getDeltaMovement().y);
        });

        helper.succeedOnTickWhen(12, () -> {
            helper.assertTrue(dummy.getHealth() < startHealth,
                    "the dummy three blocks out should have been caught by the ring, health "
                            + dummy.getHealth() + " of " + startHealth);
            helper.assertValueEqual(alreadyHit.size(), 1, "victims hit by this slam");
        });
    }

    /** One slam, one hit per victim - however many times the band sweeps over them. */
    public static void slamHitsEachVictimOnlyOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);
        IronGolem dummy = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, ARENA_CENTRE.offset(0, 0, 2));
        float startHealth = dummy.getHealth();
        Set<UUID> alreadyHit = new HashSet<>();

        helper.runAtTickTime(4L, () -> {
            // Two blocks out sits inside the band on several consecutive ticks of the expansion.
            for (int tick = 1; tick <= Shockwave.EXPANSION_TICKS; tick++) {
                boss.emitSlamRing(level, tick, alreadyHit);
            }
        });
        helper.succeedOnTickWhen(12, () -> {
            helper.assertValueEqual(alreadyHit.size(), 1, "distinct victims of one slam");
            float lost = startHealth - dummy.getHealth();
            helper.assertTrue(lost > 0.0F, "the dummy should have been hit at all");
            helper.assertTrue(lost < 30.0F,
                    "one slam is one hit of about 13 damage; this dummy lost " + lost);
        });
    }

    /** The boss never shreds its own summons with the shockwave. */
    public static void slamSparesTheBossOwnMinions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);
        AshenMinionEntity minion = helper.spawnWithNoFreeWill(
                ColossusFeature.minion().get(), ARENA_CENTRE.offset(0, 0, 3));
        minion.bindToBoss(boss);
        float startHealth = minion.getHealth();
        Set<UUID> alreadyHit = new HashSet<>();

        helper.runAtTickTime(4L, () -> {
            for (int tick = 1; tick <= Shockwave.EXPANSION_TICKS; tick++) {
                boss.emitSlamRing(level, tick, alreadyHit);
            }
        });
        helper.succeedOnTickWhen(12, () -> {
            helper.assertTrue(minion.getHealth() == startHealth,
                    "a bound minion must be immune to its own boss' shockwave, health "
                            + minion.getHealth() + " of " + startHealth);
            helper.assertTrue(alreadyHit.isEmpty(), "nothing at all should have been caught");
        });
    }

    // ------------------------------------------------------------------ minions

    /** A summon brings three minions and stops dead at the cap. */
    public static void summonBringsThreeMinionsAndStopsAtTheCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);

        helper.runAtTickTime(2L, () -> boss.summonMinions(level));
        helper.runAtTickTime(10L, () -> {
            helper.assertValueEqual(boss.aliveMinionCount(level),
                    AshenColossusEntity.MINIONS_PER_SUMMON, "minions after one summon");
            boss.summonMinions(level);
        });
        helper.runAtTickTime(20L, () -> {
            helper.assertValueEqual(boss.aliveMinionCount(level), AttackSelector.MINION_CAP,
                    "minions after two summons");
            boss.summonMinions(level);
        });
        helper.succeedOnTickWhen(30, () -> {
            helper.assertValueEqual(boss.aliveMinionCount(level), AttackSelector.MINION_CAP,
                    "the cap must hold against a third summon");
            helper.assertValueEqual(helper.getEntities(ColossusFeature.minion().get()).size(),
                    AttackSelector.MINION_CAP, "minions actually standing in the arena");
        });
    }

    /** Minions are bound to the boss that called them and crumble when it is gone. */
    public static void minionsCrumbleWhenTheBossDies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);

        helper.runAtTickTime(2L, () -> boss.summonMinions(level));
        helper.runAtTickTime(10L, () -> {
            helper.assertTrue(boss.aliveMinionCount(level) > 0, "the summon should have worked");
            helper.assertFalse(helper.getEntities(ColossusFeature.minion().get()).isEmpty(),
                    "the minions should be standing in the arena");
            boss.startDeath();
        });
        // The collapse is 70 ticks; the minions are dismissed on its first tick.
        helper.succeedOnTickWhen(100, () -> helper.assertTrue(
                helper.getEntities(ColossusFeature.minion().get()).isEmpty(),
                "every minion should have crumbled once its boss started dying"));
    }

    // ------------------------------------------------------------------ the ring of fire

    /** The phase 3 ring burns what is outside it and leaves what is inside alone. */
    public static void arenaRingBurnsOnlyWhatIsOutside(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);
        Vec3 centre = Vec3.atBottomCenterOf(helper.absolutePos(ARENA_CENTRE));

        Pig inside = helper.spawnWithNoFreeWill(EntityType.PIG, ARENA_CENTRE.offset(0, 0, 3));
        Pig outside = helper.spawnWithNoFreeWill(EntityType.PIG, ARENA_CENTRE.offset(0, 0, 10));
        float insideHealth = inside.getHealth();
        float outsideHealth = outside.getHealth();

        helper.runAtTickTime(4L, () -> ArenaRing.burnOutsiders(level, boss, centre, 8.0D, 32.0D,
                victim -> !boss.isOwnMinion(victim)));

        helper.succeedOnTickWhen(12, () -> {
            helper.assertTrue(outside.getRemainingFireTicks() > 0,
                    "the pig ten blocks out is outside a radius-8 ring and should be on fire");
            helper.assertTrue(outside.getHealth() < outsideHealth,
                    "the pig outside the ring should have been burned");
            helper.assertTrue(inside.getHealth() == insideHealth,
                    "the pig three blocks out is inside the ring and must be untouched");
            helper.assertFalse(inside.getRemainingFireTicks() > 0,
                    "the pig inside the ring must not be set alight");
        });
    }

    // ------------------------------------------------------------------ stagger, death, saving

    /** {@code /colossus stagger} drops whatever is playing and flags the boss. */
    public static void staggerInterruptsTheCurrentAttack(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, SMALL_CENTRE);

        boss.setAttack(AttackKind.SLAM);
        helper.assertTrue(boss.getAttack() == AttackKind.SLAM, "the slam should have started");

        boss.stagger();
        helper.assertTrue(boss.getAttack() == AttackKind.STAGGER,
                "stagger should replace the running attack, was " + boss.getAttack());
        helper.assertTrue(boss.isStaggered(), "the boss should be flagged as staggered");
        helper.succeed();
    }

    /** A staggered boss is the window a creator uses for the big damage number on camera. */
    public static void staggerDoublesIncomingDamage(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);
        Pig attacker = helper.spawnWithNoFreeWill(EntityType.PIG, ARENA_CENTRE.offset(3, 0, 0));
        float[] taken = new float[2];

        helper.runAtTickTime(2L, () -> {
            float before = boss.getHealth();
            boss.hurt(helper.getLevel().damageSources().mobAttack(attacker), 20.0F);
            taken[0] = before - boss.getHealth();
        });
        // Well clear of the 10-tick invulnerability window the first hit opened.
        helper.runAtTickTime(30L, () -> {
            boss.setHealth(boss.getMaxHealth());
            boss.stagger();
            float before = boss.getHealth();
            boss.hurt(helper.getLevel().damageSources().mobAttack(attacker), 20.0F);
            taken[1] = before - boss.getHealth();
        });
        helper.succeedOnTickWhen(40, () -> {
            helper.assertTrue(taken[0] > 0.0F, "the unstaggered hit should have landed at all");
            helper.assertTrue(taken[1] > taken[0],
                    "a staggered boss must take more: " + taken[1] + " vs " + taken[0]);
        });
    }

    /** The roar is invulnerable, but {@code /colossus kill} still gets through it. */
    public static void theRoarIsInvulnerableButKillIsNot(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);
        Pig attacker = helper.spawnWithNoFreeWill(EntityType.PIG, ARENA_CENTRE.offset(3, 0, 0));

        helper.runAtTickTime(2L, () -> {
            boss.setAttack(AttackKind.ROAR);
            float before = boss.getHealth();
            boss.hurt(helper.getLevel().damageSources().mobAttack(attacker), 50.0F);
            helper.assertTrue(boss.getHealth() == before,
                    "nothing should land during the roar, it lost " + (before - boss.getHealth()));
            boss.startDeath();
        });
        helper.succeedOnTickWhen(10, () -> helper.assertTrue(boss.isDeadOrDying(),
                "/colossus kill must bypass the roar invulnerability"));
    }

    /** Loot and experience arrive at the end of the collapse, not the moment the boss dies. */
    public static void killDropsLootAndExperienceAfterTheCollapse(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);

        helper.runAtTickTime(2L, boss::startDeath);
        helper.runAtTickTime(40L, () -> {
            helper.assertTrue(boss.isDeadOrDying(), "the boss should be collapsing by now");
            helper.assertTrue(helper.getEntities(EntityType.ITEM).isEmpty(),
                    "loot must wait for the end of the " + AshenColossusEntity.DEATH_TICKS
                            + "-tick collapse, not burst out of a boss that is still standing");
        });
        helper.succeedOnTickWhen(120, () -> {
            helper.assertTrue(boss.isRemoved(), "the boss should be gone once the collapse ends");
            helper.assertFalse(helper.getEntities(EntityType.ITEM).isEmpty(),
                    "the loot table should have dropped something");
            helper.assertFalse(helper.getEntities(EntityType.EXPERIENCE_ORB).isEmpty(),
                    "the death burst should have awarded experience");
        });
    }

    /** A save/load round trip keeps the arena binding and recomputes the phase from health. */
    public static void nbtRoundTripKeepsTheArenaAndPhase(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);
        BlockPos centre = helper.absolutePos(ARENA_CENTRE);

        boss.bindArena("pit", centre, 12);
        boss.setHealth(boss.getMaxHealth() * 0.5F);
        CompoundTag saved = boss.saveWithoutId(new CompoundTag());

        // Scribble over everything the tag is supposed to restore.
        boss.bindArena("elsewhere", centre.offset(4, 0, 4), 40);
        boss.setHealth(boss.getMaxHealth());
        boss.load(saved);

        helper.assertValueEqual(boss.getArenaName(), "pit", "arena name");
        helper.assertValueEqual(boss.getArenaRadius(), 12, "arena radius");
        helper.assertValueEqual(boss.getArenaCentre(), centre, "arena centre");
        helper.assertTrue(boss.hasArena(), "the boss should still be bound to an arena");
        // Phase is authoritative from health, so a half-health boss loads as phase 2 whatever
        // the tag said - a boss healed while the chunk was unloaded must not stay purple.
        helper.assertValueEqual(boss.getPhase(), BossPhase.P2.index(), "phase after load");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Spawns a goal-less Colossus at {@code pos}, bound to a test arena. The tests drive attacks
     * through the entity's own API rather than waiting on the attack chooser, so removing the
     * goals takes pathing, target acquisition and the weighted roll out of the picture.
     */
    private static AshenColossusEntity spawnBoss(GameTestHelper helper, BlockPos pos) {
        AshenColossusEntity boss = helper.spawnWithNoFreeWill(ColossusFeature.colossus().get(), pos);
        boss.bindArena("test", helper.absolutePos(pos), 20);
        boss.setAttack(AttackKind.NONE);
        return boss;
    }

    private ColossusGameTests() {
    }
}
