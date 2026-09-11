package dev.riftal.creator.features.colossus.gametest;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.colossus.ArenaRing;
import dev.riftal.creator.features.colossus.AttackKind;
import dev.riftal.creator.features.colossus.AttackSelector;
import dev.riftal.creator.features.colossus.BossPhase;
import dev.riftal.creator.features.colossus.ColossusFeature;
import dev.riftal.creator.features.colossus.FirePatches;
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
import net.minecraft.world.level.block.BaseFireBlock;
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
 * {@link #theChooserRunsARealAttackEndToEnd} is the exception and is not optional - it spawns a boss
 * with its goals intact and a live target, so the chooser, the attack goal, the trigger and the hit
 * dispatch are all exercised at least once. Without it a boss whose goals were never registered
 * would leave this whole class green.
 *
 * <h2>Two harness rules these tests learned the hard way</h2>
 *
 * <p><b>Never use {@code succeedOnTickWhen(n, ...)} as "wait n ticks, then assert".</b> It is
 * {@code createSequence().thenWaitUntil((long) n, criterion).thenSucceed()}, and
 * {@code GameTestSequence#tick} <em>fails</em> the test when the criterion first holds on any tick
 * other than exactly {@code n} ("Succeeded in invalid tick"). It only means "must become true on
 * tick n and not before". Everything here uses {@code startSequence()} instead:
 * {@code thenIdle(n)} to wait a fixed number of ticks, {@code thenWaitUntil(criterion)} to poll
 * until something settles, {@code thenExecute(assertions)} for a hard check.
 *
 * <p><b>The floor of a test structure is at relative y = 1, not y = 0.</b> A GameTest structure
 * block sits one block below the template origin ({@code StructureBlockEntity#structurePos} is
 * {@code (0, 1, 0)}), and {@code helper.absolutePos} is measured from the structure block. Both of
 * our templates put their polished-andesite slab on template layer 0, so relative y = 1 <em>is</em>
 * that slab: an entity spawned there starts inside the floor, falls straight through it (vanilla
 * only clips a fall when the box is above the block face) and ends up under the arena - outside
 * {@code helper.getBounds()}, where {@code getEntities} can no longer see anything it drops.
 * Entities therefore spawn at relative y = 2, standing on the floor.
 */
public final class ColossusGameTests {

    /** Middle of the {@code arena_24} floor, standing on it rather than in it. */
    private static final BlockPos ARENA_CENTRE = new BlockPos(12, 2, 12);

    /** Middle of the 9x9 {@code empty} floor. */
    private static final BlockPos SMALL_CENTRE = new BlockPos(4, 2, 4);

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
        helper.startSequence()
                .thenIdle(6)
                .thenExecute(() -> helper.assertTrue(boss.bossEvent().getProgress() == 1.0F,
                        "boss bar should be full, was " + boss.bossEvent().getProgress()))
                .thenSucceed();
    }

    /** Damage alone drives the phase machine: the bar recolours and phase 3 darkens the screen. */
    public static void phaseThresholdsFollowHealth(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, SMALL_CENTRE);

        // The phase machine runs from the boss' own entity tick, and the gametest ticker and the
        // level tick interleave differently on the two loaders, so poll for the transition rather
        // than naming the tick it has to land on.
        helper.startSequence()
                .thenExecute(() -> boss.setHealth(boss.getMaxHealth() * 0.5F))
                .thenWaitUntil(() -> helper.assertValueEqual(
                        boss.getPhase(), BossPhase.P2.index(), "phase at 50% health"))
                .thenExecute(() -> {
                    helper.assertTrue(boss.bossEvent().getColor() == BossEvent.BossBarColor.RED,
                            "the phase 2 bar is red, was " + boss.bossEvent().getColor());
                    helper.assertFalse(boss.bossEvent().shouldDarkenScreen(),
                            "the screen only darkens in phase 3");
                    boss.setHealth(boss.getMaxHealth() * 0.2F);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(
                        boss.getPhase(), BossPhase.P3.index(), "phase at 20% health"))
                .thenExecute(() -> {
                    helper.assertTrue(boss.bossEvent().getColor() == BossEvent.BossBarColor.PURPLE,
                            "the phase 3 bar is purple, was " + boss.bossEvent().getColor());
                    helper.assertTrue(boss.bossEvent().shouldDarkenScreen(),
                            "phase 3 should darken the screen");
                })
                .thenSucceed();
    }

    /** Healing the boss must not walk the fight back to an earlier phase mid-take. */
    public static void phasesNeverRunBackwardsOnHealing(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, SMALL_CENTRE);

        helper.startSequence()
                .thenExecute(() -> boss.setHealth(boss.getMaxHealth() * 0.2F))
                .thenWaitUntil(() -> helper.assertValueEqual(
                        boss.getPhase(), BossPhase.P3.index(), "phase at 20% health"))
                .thenExecute(() -> boss.setHealth(boss.getMaxHealth()))
                // Long enough for the phase machine to have run many times on full health.
                .thenIdle(20)
                .thenExecute(() -> helper.assertValueEqual(
                        boss.getPhase(), BossPhase.P3.index(), "phase after healing back to full"))
                .thenSucceed();
    }

    /** Phase 3 hangs the enrage speed modifier on the boss exactly once. */
    public static void phaseThreeEnragesTheBoss(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, SMALL_CENTRE);
        double base = boss.getAttributeValue(Attributes.MOVEMENT_SPEED);

        helper.startSequence()
                .thenExecute(() -> {
                    boss.forcePhase(BossPhase.P3);
                    // Adding the same AttributeModifier id twice throws in 1.21 - re-entering the
                    // phase must be a no-op, which is what /colossus phase 3 does on a second take.
                    boss.forcePhase(BossPhase.P3);
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    double enraged = boss.getAttributeValue(Attributes.MOVEMENT_SPEED);
                    helper.assertTrue(Math.abs(enraged - base * 1.5D) < 1.0E-6D,
                            "phase 3 is +50% movement speed: expected " + (base * 1.5D)
                                    + ", got " + enraged);
                })
                .thenSucceed();
    }

    /** Dropping back out of phase 3 with {@code /colossus phase 1} takes the speed bonus with it. */
    public static void leavingPhaseThreeRemovesTheEnrage(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, SMALL_CENTRE);
        double base = boss.getAttributeValue(Attributes.MOVEMENT_SPEED);

        helper.startSequence()
                .thenExecute(() -> boss.forcePhase(BossPhase.P3))
                .thenIdle(6)
                .thenExecute(() -> {
                    // Prove the modifier was really on before asserting that it came off, or the
                    // test would pass just as happily against an enrage that never applied.
                    double enraged = boss.getAttributeValue(Attributes.MOVEMENT_SPEED);
                    helper.assertTrue(enraged > base + 1.0E-6D,
                            "phase 3 should have raised the speed first: " + enraged + " vs " + base);
                    boss.forcePhase(BossPhase.P1);
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    double now = boss.getAttributeValue(Attributes.MOVEMENT_SPEED);
                    helper.assertTrue(Math.abs(now - base) < 1.0E-6D,
                            "the enrage modifier should be gone: expected " + base + ", got " + now);
                })
                .thenSucceed();
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
        Set<UUID> alreadyHit = new HashSet<>();
        float[] startHealth = new float[1];

        helper.startSequence()
                // Let both entities land and settle before the ring measures distances.
                .thenIdle(4)
                .thenExecute(() -> {
                    startHealth[0] = dummy.getHealth();
                    for (int tick = 1; tick <= Shockwave.EXPANSION_TICKS; tick++) {
                        boss.emitSlamRing(level, tick, alreadyHit);
                    }
                    // Asserted in the same tick the ring lands: gravity would eat the upward kick
                    // within a couple of ticks and the test would read as a false negative.
                    helper.assertTrue(dummy.getDeltaMovement().y > 0.0D,
                            "the shockwave throws victims up as well as out, dy was "
                                    + dummy.getDeltaMovement().y);
                    helper.assertTrue(dummy.getHealth() < startHealth[0],
                            "the dummy three blocks out should have been caught by the ring, health "
                                    + dummy.getHealth() + " of " + startHealth[0]);
                    helper.assertValueEqual(alreadyHit.size(), 1, "victims hit by this slam");
                })
                .thenSucceed();
    }

    /** One slam, one hit per victim - however many times the band sweeps over them. */
    public static void slamHitsEachVictimOnlyOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);
        IronGolem dummy = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, ARENA_CENTRE.offset(0, 0, 2));
        Set<UUID> alreadyHit = new HashSet<>();
        float[] startHealth = new float[1];

        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    startHealth[0] = dummy.getHealth();
                    // Two blocks out sits inside the band on several consecutive ticks of the
                    // expansion.
                    for (int tick = 1; tick <= Shockwave.EXPANSION_TICKS; tick++) {
                        boss.emitSlamRing(level, tick, alreadyHit);
                    }
                    helper.assertValueEqual(alreadyHit.size(), 1, "distinct victims of one slam");
                    float lost = startHealth[0] - dummy.getHealth();
                    helper.assertTrue(lost > 0.0F, "the dummy should have been hit at all");
                    helper.assertTrue(lost < 30.0F,
                            "one slam is one hit of about 13 damage; this dummy lost " + lost);
                })
                .thenSucceed();
    }

    /** The boss never shreds its own summons with the shockwave. */
    public static void slamSparesTheBossOwnMinions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);
        AshenMinionEntity minion = helper.spawnWithNoFreeWill(
                ColossusFeature.minion().get(), ARENA_CENTRE.offset(0, 0, 3));
        minion.bindToBoss(boss);
        Set<UUID> alreadyHit = new HashSet<>();
        float[] startHealth = new float[1];

        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    startHealth[0] = minion.getHealth();
                    for (int tick = 1; tick <= Shockwave.EXPANSION_TICKS; tick++) {
                        boss.emitSlamRing(level, tick, alreadyHit);
                    }
                    helper.assertTrue(minion.getHealth() == startHealth[0],
                            "a bound minion must be immune to its own boss' shockwave, health "
                                    + minion.getHealth() + " of " + startHealth[0]);
                    helper.assertTrue(alreadyHit.isEmpty(), "nothing at all should have been caught");
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ minions

    /** A summon brings three minions and stops dead at the cap. */
    public static void summonBringsThreeMinionsAndStopsAtTheCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);

        helper.startSequence()
                .thenExecute(() -> boss.summonMinions(level))
                .thenIdle(6)
                .thenExecute(() -> {
                    helper.assertValueEqual(boss.aliveMinionCount(level),
                            AshenColossusEntity.MINIONS_PER_SUMMON, "minions after one summon");
                    boss.summonMinions(level);
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    helper.assertValueEqual(boss.aliveMinionCount(level), AttackSelector.MINION_CAP,
                            "minions after two summons");
                    boss.summonMinions(level);
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    helper.assertValueEqual(boss.aliveMinionCount(level), AttackSelector.MINION_CAP,
                            "the cap must hold against a third summon");
                    helper.assertValueEqual(helper.getEntities(ColossusFeature.minion().get()).size(),
                            AttackSelector.MINION_CAP, "minions actually standing in the arena");
                })
                .thenSucceed();
    }

    /** Minions are bound to the boss that called them and crumble when it is gone. */
    public static void minionsCrumbleWhenTheBossDies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);

        helper.startSequence()
                .thenExecute(() -> boss.summonMinions(level))
                .thenIdle(6)
                .thenExecute(() -> {
                    helper.assertTrue(boss.aliveMinionCount(level) > 0, "the summon should have worked");
                    helper.assertFalse(helper.getEntities(ColossusFeature.minion().get()).isEmpty(),
                            "the minions should be standing in the arena");
                    boss.startDeath();
                })
                // The collapse is 70 ticks; the minions are dismissed on its first tick.
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getEntities(ColossusFeature.minion().get()).isEmpty(),
                        "every minion should have crumbled once its boss started dying"))
                .thenSucceed();
    }

    // ------------------------------------------------------------------ the ring of fire

    /** The phase 3 ring burns what is outside it and leaves what is inside alone. */
    public static void arenaRingBurnsOnlyWhatIsOutside(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);
        Vec3 centre = Vec3.atBottomCenterOf(helper.absolutePos(ARENA_CENTRE));

        Pig inside = helper.spawnWithNoFreeWill(EntityType.PIG, ARENA_CENTRE.offset(0, 0, 3));
        Pig outside = helper.spawnWithNoFreeWill(EntityType.PIG, ARENA_CENTRE.offset(0, 0, 10));
        float[] health = new float[2];

        helper.startSequence()
                // Both pigs land on the floor before the ring measures where they are standing.
                .thenIdle(4)
                .thenExecute(() -> {
                    health[0] = inside.getHealth();
                    health[1] = outside.getHealth();
                    ArenaRing.burnOutsiders(level, boss, centre, 8.0D, 32.0D,
                            victim -> !boss.isOwnMinion(victim));
                })
                // Same tick as the burn: fire ticks tick down and nothing else in this arena can
                // set anything alight, so there is no reason to wait and every reason not to.
                .thenExecute(() -> {
                    helper.assertTrue(ArenaRing.isOutside(centre, outside, 8.0D),
                            "the far pig must actually be outside the radius-8 ring for this test "
                                    + "to mean anything; it is standing at " + outside.position()
                                    + " and the ring is centred on " + centre);
                    helper.assertTrue(outside.getRemainingFireTicks() > 0,
                            "the pig ten blocks out is outside a radius-8 ring and should be on fire");
                    helper.assertTrue(outside.getHealth() < health[1],
                            "the pig outside the ring should have been burned");
                    helper.assertTrue(inside.getHealth() == health[0],
                            "the pig three blocks out is inside the ring and must be untouched");
                    helper.assertFalse(inside.getRemainingFireTicks() > 0,
                            "the pig inside the ring must not be set alight");
                })
                .thenSucceed();
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

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    float before = boss.getHealth();
                    boss.hurt(helper.getLevel().damageSources().mobAttack(attacker), 20.0F);
                    taken[0] = before - boss.getHealth();
                })
                // Well clear of the 10-tick invulnerability window the first hit opened.
                .thenIdle(30)
                .thenExecute(() -> {
                    boss.setHealth(boss.getMaxHealth());
                    boss.stagger();
                    float before = boss.getHealth();
                    boss.hurt(helper.getLevel().damageSources().mobAttack(attacker), 20.0F);
                    taken[1] = before - boss.getHealth();
                })
                .thenExecute(() -> {
                    helper.assertTrue(taken[0] > 0.0F, "the unstaggered hit should have landed at all");
                    helper.assertTrue(taken[1] > taken[0],
                            "a staggered boss must take more: " + taken[1] + " vs " + taken[0]);
                })
                .thenSucceed();
    }

    /** The roar is invulnerable, but {@code /colossus kill} still gets through it. */
    public static void theRoarIsInvulnerableButKillIsNot(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);
        Pig attacker = helper.spawnWithNoFreeWill(EntityType.PIG, ARENA_CENTRE.offset(3, 0, 0));

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    boss.setAttack(AttackKind.ROAR);
                    float before = boss.getHealth();
                    boss.hurt(helper.getLevel().damageSources().mobAttack(attacker), 50.0F);
                    helper.assertTrue(boss.getHealth() == before,
                            "nothing should land during the roar, it lost "
                                    + (before - boss.getHealth()));
                    boss.startDeath();
                })
                .thenExecute(() -> helper.assertTrue(boss.isDeadOrDying(),
                        "/colossus kill must bypass the roar invulnerability"))
                .thenSucceed();
    }

    /** Loot and experience arrive at the end of the collapse, not the moment the boss dies. */
    public static void killDropsLootAndExperienceAfterTheCollapse(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(boss::startDeath)
                .thenIdle(38)
                .thenExecute(() -> {
                    helper.assertTrue(boss.isDeadOrDying(), "the boss should be collapsing by now");
                    helper.assertTrue(helper.getEntities(EntityType.ITEM).isEmpty(),
                            "loot must wait for the end of the " + AshenColossusEntity.DEATH_TICKS
                                    + "-tick collapse, not burst out of a boss that is still standing");
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(boss.isRemoved(), "the boss should be gone once the collapse ends");
                    helper.assertFalse(helper.getEntities(EntityType.ITEM).isEmpty(),
                            "the loot table should have dropped something");
                    helper.assertFalse(helper.getEntities(EntityType.EXPERIENCE_ORB).isEmpty(),
                            "the death burst should have awarded experience");
                })
                .thenSucceed();
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

    // ------------------------------------------------------------------ the autonomous fight

    /**
     * The one test that runs the real AI: goals, a live target, the chooser, the attack goal and
     * the hit tick. Everything else in this class drives the entity API directly, which is fast and
     * deterministic but would stay green against a boss whose goals were never registered at all.
     *
     * <p>Phase 1 has exactly one move, so with a target three blocks away the chooser's answer is
     * known: SLAM. The assertions follow one full cycle - idle, a chosen attack, the hit landing on
     * the kind's own hit tick, the slot handed back and both cooldowns started.
     */
    public static void theChooserRunsARealAttackEndToEnd(GameTestHelper helper) {
        AshenColossusEntity boss = spawnLiveBoss(helper, ARENA_CENTRE);
        IronGolem victim = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM,
                ARENA_CENTRE.offset(0, 0, 3));
        float[] startHealth = new float[1];

        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    startHealth[0] = victim.getHealth();
                    // The boss has no natural target: NearestAttackableTargetGoal skips creative
                    // players and an iron golem is not a Player at all, so the fight is started by
                    // hand. Everything after this line is the boss' own AI.
                    boss.setTarget(victim);
                    helper.assertTrue(boss.getAttack() == AttackKind.NONE,
                            "the boss should still be idle, was " + boss.getAttack());
                })
                // AttackChooserGoal -> AttackSelector -> setAttack, then SlamGoal picks it up.
                .thenWaitUntil(() -> helper.assertTrue(boss.getAttack() == AttackKind.SLAM,
                        "phase 1's only move is the slam; the chooser went with " + boss.getAttack()))
                // Hit tick 20, then eight ticks of ring: comfortably inside the clip.
                .thenIdle(AttackKind.SLAM.hitTick(0) + Shockwave.EXPANSION_TICKS + 2)
                .thenExecute(() -> helper.assertTrue(victim.getHealth() < startHealth[0],
                        "the slam the boss chose for itself should have landed on the dummy three "
                                + "blocks away, health " + victim.getHealth() + " of "
                                + startHealth[0]))
                // The goal owns the boss for the whole 40-tick clip and hands the slot back at the
                // end; without that the boss would be stuck in one attack forever.
                .thenWaitUntil(() -> helper.assertTrue(boss.getAttack() == AttackKind.NONE,
                        "the attack slot should be free again, holds " + boss.getAttack()))
                .thenExecute(() -> {
                    helper.assertFalse(boss.globalCooldownReady(),
                            "finishing an attack must start the global cooldown");
                    helper.assertTrue(boss.cooldowns().slam() > 0,
                            "the slam's own cooldown should be running, was "
                                    + boss.cooldowns().slam());
                    helper.assertTrue(boss.attackHistory().contains(AttackKind.SLAM),
                            "the pick should be in the history that stops immediate repeats");
                })
                .thenSucceed();
    }

    /**
     * {@code /colossus} resolves the nearest boss, not "a" boss. Runs the real command tree through
     * the dispatcher - the tree itself had no test of any kind.
     */
    public static void commandsResolveTheNearestBoss(GameTestHelper helper) {
        AshenColossusEntity near = spawnBoss(helper, ARENA_CENTRE);
        AshenColossusEntity far = spawnBoss(helper, ARENA_CENTRE.offset(0, 0, 9));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(ARENA_CENTRE));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);

        try {
            helper.assertTrue(near.getHealth() == near.getMaxHealth(), "both bosses start full");
            helper.assertTrue(far.getHealth() == far.getMaxHealth(), "both bosses start full");

            helper.getLevel().getServer().getCommands().performPrefixedCommand(
                    player.createCommandSourceStack().withPermission(2), "colossus hp 50");

            helper.assertTrue(near.getHealth() < near.getMaxHealth(),
                    "the boss the player is standing on should have taken the command, hp is "
                            + near.getHealth());
            helper.assertTrue(far.getHealth() == far.getMaxHealth(),
                    "the boss nine blocks away should be untouched, hp is " + far.getHealth());
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ the camera is not a target

    /**
     * A creative or spectator camera is never damaged, ignited or shoved by the fight.
     *
     * <p>Every assertion is paired with a survival-side control standing in the same place - an iron
     * golem in the shockwave band, a pig outside the ring - so the test cannot pass by the area
     * query simply missing that patch of arena.
     */
    public static void theFightLeavesACreativeCameraAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);
        // makeMockServerPlayerInLevel returns a player whose isCreative() is true - exactly the
        // second camera operator this guard exists for.
        ServerPlayer camera = helper.makeMockServerPlayerInLevel();
        IronGolem control = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM,
                ARENA_CENTRE.offset(0, 0, 3));
        Pig outsideControl = helper.spawnWithNoFreeWill(EntityType.PIG,
                ARENA_CENTRE.offset(2, 0, 10));

        Vec3 centre = Vec3.atBottomCenterOf(helper.absolutePos(ARENA_CENTRE));
        float[] health = new float[2];
        Set<UUID> alreadyHit = new HashSet<>();

        helper.startSequence()
                // Let the two control mobs land on the floor before anything measures distances.
                .thenIdle(4)
                .thenExecute(() -> {
                    Vec3 inTheBand = centre.add(0.0D, 0.0D, 3.0D);
                    camera.moveTo(inTheBand.x, inTheBand.y, inTheBand.z, 0.0F, 0.0F);
                    camera.setDeltaMovement(Vec3.ZERO);
                    helper.assertTrue(camera.isCreative(),
                            "the mock player should be in creative mode");
                    health[0] = camera.getHealth();
                    health[1] = control.getHealth();

                    for (int tick = 1; tick <= Shockwave.EXPANSION_TICKS; tick++) {
                        boss.emitSlamRing(level, tick, alreadyHit);
                    }

                    helper.assertTrue(control.getHealth() < health[1],
                            "the control golem standing beside the camera must be hit, or this "
                                    + "test proves nothing about the camera");
                    helper.assertValueEqual(alreadyHit.size(), 1, "victims of the slam");
                    helper.assertFalse(alreadyHit.contains(camera.getUUID()),
                            "the shockwave must not count a camera as a victim");
                    // hurt() is filtered by vanilla for creative players; setDeltaMovement is not,
                    // and hurtMarked would push the shove all the way to their client.
                    helper.assertTrue(camera.getDeltaMovement().equals(Vec3.ZERO),
                            "the slam must not shove a creative camera, delta was "
                                    + camera.getDeltaMovement());
                })
                .thenExecute(() -> {
                    // Ten blocks out of a radius-8 ring: squarely in the fire, beside a pig that
                    // is about to prove the ring reaches this far.
                    Vec3 outside = centre.add(0.0D, 0.0D, 10.0D);
                    camera.moveTo(outside.x, outside.y, outside.z, 0.0F, 0.0F);
                    health[0] = camera.getHealth();
                    float pigHealth = outsideControl.getHealth();

                    ArenaRing.burnOutsiders(level, boss, centre, 8.0D, 32.0D,
                            victim -> !boss.isOwnMinion(victim));

                    helper.assertTrue(ArenaRing.isOutside(centre, camera, 8.0D),
                            "the camera has to actually be outside the ring for this to mean "
                                    + "anything; it is standing at " + camera.position());
                    helper.assertTrue(outsideControl.getRemainingFireTicks() > 0
                                    && outsideControl.getHealth() < pigHealth,
                            "the control pig outside the ring must burn, or the ring never reached "
                                    + "this part of the arena");
                    // setRemainingFireTicks ignores creative mode and fireImmune alike, so this is
                    // the assertion that keeps a full-screen fire overlay out of the wide shot.
                    // "Not alight" is <= 0, not == 0: Entity.java:195 initialises the counter to
                    // -getFireImmuneTicks() and Entity#move (Entity.java:728-729) resets it to that
                    // same value every tick the entity spends out of fire, and Player overrides
                    // getFireImmuneTicks() to 20 (Player.java:470). A player who has never burned
                    // therefore reads -20; ArenaRing.burnOutsiders would leave BURN_TICKS (60).
                    helper.assertTrue(camera.getRemainingFireTicks() <= 0,
                            "the ring must not set a creative camera alight, fire ticks were "
                                    + camera.getRemainingFireTicks());
                    helper.assertFalse(camera.isOnFire(),
                            "a creative camera must never render the fire overlay");
                    helper.assertTrue(camera.getHealth() == health[0],
                            "the ring must not burn a creative camera");
                })
                .thenExecute(() -> helper.getLevel().getServer().getPlayerList().remove(camera))
                .thenSucceed();
    }

    // ------------------------------------------------------------------ arena, fire, cleanup

    /**
     * Moving the arena mid-take moves the ring with it. The ring's radius is recomputed every tick
     * from {@code ticksInPhase3}, so re-binding has to reset that clock or the very next tick
     * recomputes the radius from a stale one and the ring snaps to its 6-block floor.
     */
    public static void rebindingTheArenaRestartsTheRing(GameTestHelper helper) {
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);
        BlockPos centre = helper.absolutePos(ARENA_CENTRE);

        helper.startSequence()
                .thenExecute(() -> boss.forcePhase(BossPhase.P3))
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(boss.ticksInPhase3() > 0,
                            "the ring clock should have been running for 30 ticks");
                    boss.bindArena("wider", centre, 12);
                    helper.assertValueEqual(boss.ticksInPhase3(), 0, "ring clock after a re-bind");
                    helper.assertTrue(boss.getRingRadius() == 12.0F,
                            "the ring should jump to the new circle, sits at "
                                    + boss.getRingRadius());
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    float radius = boss.getRingRadius();
                    // One second of closing at 0.35 blocks/s is 0.35 blocks. Anything near the
                    // 6-block floor means the ring collapsed instead of re-starting.
                    helper.assertTrue(radius > 11.0F && radius <= 12.0F,
                            "the ring should be closing from 12 blocks, not collapsed; it is at "
                                    + radius);
                })
                .thenSucceed();
    }

    /**
     * The end of the fight puts the arena out. The scheduled task <em>is</em> the cleanup, so
     * cancelling it would leave every patch from the last lava-rain volley burning into take two.
     */
    public static void deathExtinguishesTheFirePatches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AshenColossusEntity boss = spawnBoss(helper, ARENA_CENTRE);
        // One block above the floor, the way an ash bomb lights its patch.
        BlockPos patchCentre = helper.absolutePos(ARENA_CENTRE.offset(5, 0, 5));
        int[] placed = new int[1];

        helper.startSequence()
                .thenExecute(() -> {
                    // A lifetime far longer than this test: the fire must go out because the boss
                    // died, not because it burned down.
                    placed[0] = FirePatches.scatter(level, patchCentre, 1, 2000);
                    helper.assertTrue(placed[0] > 0,
                            "the patch should have lit at all; the test floor may not be solid");
                    helper.assertTrue(FirePatches.pendingPatches() > 0,
                            "the patch should be waiting for its cleanup");
                    helper.assertTrue(anyFireAround(level, patchCentre),
                            "there should be fire on the floor before the boss dies");
                    boss.startDeath();
                })
                // tickDeath runs the cleanup on the first tick of the collapse.
                .thenWaitUntil(() -> helper.assertFalse(anyFireAround(level, patchCentre),
                        "/colossus kill must put the arena out, not merely forget about it"))
                .thenExecute(() -> helper.assertValueEqual(FirePatches.pendingPatches(), 0,
                        "pending fire patches after the fight"))
                .thenSucceed();
    }

    // ------------------------------------------------------------------ helpers

    /** True while any fire block is standing in the 3x3 patch around {@code centre}. */
    private static boolean anyFireAround(ServerLevel level, BlockPos centre) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -2; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (level.getBlockState(centre.offset(dx, dy, dz))
                            .getBlock() instanceof BaseFireBlock) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

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

    /**
     * Spawns a Colossus with its goals intact - {@code spawn}, not {@code spawnWithNoFreeWill} -
     * and with no spawn animation in the way. {@code GameTestHelper#spawn} does not call
     * {@code finalizeSpawn}, so the 60-tick invulnerable entrance never starts and the boss is
     * ready to fight on the first tick; the arena still has to be bound by hand.
     */
    private static AshenColossusEntity spawnLiveBoss(GameTestHelper helper, BlockPos pos) {
        AshenColossusEntity boss = helper.spawn(ColossusFeature.colossus().get(), pos);
        boss.bindArena("test", helper.absolutePos(pos), 20);
        boss.setAttack(AttackKind.NONE);
        return boss;
    }

    private ColossusGameTests() {
    }
}
