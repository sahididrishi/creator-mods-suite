package dev.riftal.creator.features.colossus.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge discovery stub for the {@code colossus} feature. Auto-scanned because of
 * {@link GameTestHolder}; {@code creator_colossus} is already listed in
 * {@code neoforge.enabledGameTestNamespaces}.
 *
 * <p>{@link PrefixGameTestTemplate}(false) keeps the template id at {@code creator_colossus:empty}
 * instead of {@code creator_colossus:Colossusneoforgegametests.empty}, so templates are named here
 * as bare paths.
 */
@GameTestHolder("creator_colossus")
@PrefixGameTestTemplate(false)
public class ColossusNeoForgeGameTests {

    /** 9x9x9 polished andesite floor, shipped by the scaffold. */
    private static final String EMPTY = "empty";

    /** 24x12x24 floor: big enough for the seven-block shockwave, the ring and six minions. */
    private static final String ARENA = "arena_24";

    @GameTest(template = EMPTY)
    public void featureIsEnabled(GameTestHelper helper) {
        ColossusGameTests.featureIsEnabled(helper);
    }

    @GameTest(template = EMPTY)
    public void spawnsAtFullHealthInPhaseOne(GameTestHelper helper) {
        ColossusGameTests.spawnsAtFullHealthInPhaseOne(helper);
    }

    @GameTest(template = EMPTY)
    public void phaseThresholdsFollowHealth(GameTestHelper helper) {
        ColossusGameTests.phaseThresholdsFollowHealth(helper);
    }

    @GameTest(template = EMPTY)
    public void phasesNeverRunBackwardsOnHealing(GameTestHelper helper) {
        ColossusGameTests.phasesNeverRunBackwardsOnHealing(helper);
    }

    @GameTest(template = EMPTY)
    public void phaseThreeEnragesTheBoss(GameTestHelper helper) {
        ColossusGameTests.phaseThreeEnragesTheBoss(helper);
    }

    @GameTest(template = EMPTY)
    public void leavingPhaseThreeRemovesTheEnrage(GameTestHelper helper) {
        ColossusGameTests.leavingPhaseThreeRemovesTheEnrage(helper);
    }

    @GameTest(template = EMPTY)
    public void bossBarTracksSeenPlayers(GameTestHelper helper) {
        ColossusGameTests.bossBarTracksSeenPlayers(helper);
    }

    @GameTest(template = EMPTY)
    public void staggerInterruptsTheCurrentAttack(GameTestHelper helper) {
        ColossusGameTests.staggerInterruptsTheCurrentAttack(helper);
    }

    @GameTest(template = ARENA)
    public void slamDamagesAndLaunchesADummyInTheRing(GameTestHelper helper) {
        ColossusGameTests.slamDamagesAndLaunchesADummyInTheRing(helper);
    }

    @GameTest(template = ARENA)
    public void slamHitsEachVictimOnlyOnce(GameTestHelper helper) {
        ColossusGameTests.slamHitsEachVictimOnlyOnce(helper);
    }

    @GameTest(template = ARENA)
    public void slamSparesTheBossOwnMinions(GameTestHelper helper) {
        ColossusGameTests.slamSparesTheBossOwnMinions(helper);
    }

    @GameTest(template = ARENA)
    public void summonBringsThreeMinionsAndStopsAtTheCap(GameTestHelper helper) {
        ColossusGameTests.summonBringsThreeMinionsAndStopsAtTheCap(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public void minionsCrumbleWhenTheBossDies(GameTestHelper helper) {
        ColossusGameTests.minionsCrumbleWhenTheBossDies(helper);
    }

    @GameTest(template = ARENA)
    public void arenaRingBurnsOnlyWhatIsOutside(GameTestHelper helper) {
        ColossusGameTests.arenaRingBurnsOnlyWhatIsOutside(helper);
    }

    @GameTest(template = ARENA)
    public void staggerDoublesIncomingDamage(GameTestHelper helper) {
        ColossusGameTests.staggerDoublesIncomingDamage(helper);
    }

    @GameTest(template = ARENA)
    public void theRoarIsInvulnerableButKillIsNot(GameTestHelper helper) {
        ColossusGameTests.theRoarIsInvulnerableButKillIsNot(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public void killDropsLootAndExperienceAfterTheCollapse(GameTestHelper helper) {
        ColossusGameTests.killDropsLootAndExperienceAfterTheCollapse(helper);
    }

    @GameTest(template = ARENA)
    public void nbtRoundTripKeepsTheArenaAndPhase(GameTestHelper helper) {
        ColossusGameTests.nbtRoundTripKeepsTheArenaAndPhase(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public void theChooserRunsARealAttackEndToEnd(GameTestHelper helper) {
        ColossusGameTests.theChooserRunsARealAttackEndToEnd(helper);
    }

    @GameTest(template = ARENA)
    public void commandsResolveTheNearestBoss(GameTestHelper helper) {
        ColossusGameTests.commandsResolveTheNearestBoss(helper);
    }

    @GameTest(template = ARENA)
    public void theFightLeavesACreativeCameraAlone(GameTestHelper helper) {
        ColossusGameTests.theFightLeavesACreativeCameraAlone(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public void rebindingTheArenaRestartsTheRing(GameTestHelper helper) {
        ColossusGameTests.rebindingTheArenaRestartsTheRing(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public void deathExtinguishesTheFirePatches(GameTestHelper helper) {
        ColossusGameTests.deathExtinguishesTheFirePatches(helper);
    }
}
