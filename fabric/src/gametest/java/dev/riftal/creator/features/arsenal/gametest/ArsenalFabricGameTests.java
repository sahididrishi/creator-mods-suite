package dev.riftal.creator.features.arsenal.gametest;

import dev.riftal.creator.features.arsenal.ArsenalFeature;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric discovery stub for the {@code arsenal} feature. Already listed in the {@code fabric-gametest}
 * entrypoint of {@code fabric/src/gametest/resources/fabric.mod.json} - do not edit that file, and
 * never move these methods to a sibling class: a class that is not in that entrypoint list is
 * silently never run.
 *
 * <p>Fabric uses {@code template()} verbatim, so a template must be written as a full
 * {@code namespace:path}. Structures live in
 * {@code common/src/main/resources/data/creator_arsenal/structure/}.
 *
 * <p>The {@code batch} names mirror the other stub one for one, and for the reason spelled out on
 * {@link ArsenalGameTests}: batch-mates tick side by side in one shared level, 14 blocks apart, so
 * any test asserting on health, fire, effects, position or motion runs alone. Keep the two stubs in
 * step - a batch name that exists on one loader only re-opens the bug on the other.
 */
public class ArsenalFabricGameTests implements FabricGameTest {

    private static final String EMPTY = ArsenalFeature.NAMESPACE + ":empty";

    @GameTest(template = EMPTY)
    public void featureIsEnabled(GameTestHelper helper) {
        ArsenalGameTests.featureIsEnabled(helper);
    }

    @GameTest(template = EMPTY)
    public void weaponsCarryTheirRarityAndAttributes(GameTestHelper helper) {
        ArsenalGameTests.weaponsCarryTheirRarityAndAttributes(helper);
    }

    @GameTest(template = EMPTY)
    public void giveCommandHandsOutAllFourAndArrows(GameTestHelper helper) {
        ArsenalGameTests.giveCommandHandsOutAllFourAndArrows(helper);
    }

    @GameTest(template = EMPTY)
    public void giveCommandRejectsAnUnknownWeapon(GameTestHelper helper) {
        ArsenalGameTests.giveCommandRejectsAnUnknownWeapon(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 120, batch = "creator_arsenal_storm_charged")
    public void stormArrowChargedStrikesWithoutFire(GameTestHelper helper) {
        ArsenalGameTests.stormArrowChargedStrikesWithoutFire(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 120, batch = "creator_arsenal_storm_uncharged")
    public void stormArrowUnchargedNeverStrikes(GameTestHelper helper) {
        ArsenalGameTests.stormArrowUnchargedNeverStrikes(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_arsenal_hammer_lift")
    public void hammerLiftsEverythingInRange(GameTestHelper helper) {
        ArsenalGameTests.hammerLiftsEverythingInRange(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 120, batch = "creator_arsenal_hammer_slam")
    public void hammerSlamsAfterTheHangTime(GameTestHelper helper) {
        ArsenalGameTests.hammerSlamsAfterTheHangTime(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_arsenal_scythe_sweep")
    public void scytheHealsAndSweeps(GameTestHelper helper) {
        ArsenalGameTests.scytheHealsAndSweeps(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 120, batch = "creator_arsenal_scythe_wisp")
    public void scytheKillGrantsAbsorptionAfterTheWisp(GameTestHelper helper) {
        ArsenalGameTests.scytheKillGrantsAbsorptionAfterTheWisp(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "creator_arsenal_grapple_bite")
    public void grappleHookBitesAndReelsThePlayer(GameTestHelper helper) {
        ArsenalGameTests.grappleHookBitesAndReelsThePlayer(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 120, batch = "creator_arsenal_grapple_cut")
    public void grappleSecondUseCutsTheLine(GameTestHelper helper) {
        ArsenalGameTests.grappleSecondUseCutsTheLine(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_arsenal_slam_window")
    public void slamDamageSurvivesTheFallDamageWindow(GameTestHelper helper) {
        ArsenalGameTests.slamDamageSurvivesTheFallDamageWindow(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 120, batch = "creator_arsenal_storm_blast")
    public void stormArrowBlastFullyHitsWhatItStruck(GameTestHelper helper) {
        ArsenalGameTests.stormArrowBlastFullyHitsWhatItStruck(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 120, batch = "creator_arsenal_scythe_vanilla")
    public void scytheDoesNotSweepOnTopOfVanillasSweep(GameTestHelper helper) {
        ArsenalGameTests.scytheDoesNotSweepOnTopOfVanillasSweep(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "creator_arsenal_grapple_reel")
    public void grappleReelCarriesThePlayerAcrossTheGap(GameTestHelper helper) {
        ArsenalGameTests.grappleReelCarriesThePlayerAcrossTheGap(helper);
    }
}
