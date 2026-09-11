package dev.riftal.creator.features.arsenal.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge discovery stub for the {@code arsenal} feature. Auto-scanned because of
 * {@link GameTestHolder}; {@code creator_arsenal} is already listed in
 * {@code neoforge.enabledGameTestNamespaces}.
 *
 * <p>{@link PrefixGameTestTemplate}(false) keeps the template id at {@code creator_arsenal:empty}
 * instead of {@code creator_arsenal:Arsenalneoforgegametests.empty}. Methods here mirror
 * {@code ArsenalFabricGameTests} one for one - never a sibling class.
 *
 * <p>The {@code batch} names mirror the other stub one for one, and for the reason spelled out on
 * {@link ArsenalGameTests}: batch-mates tick side by side in one shared level, 14 blocks apart, so
 * any test asserting on health, fire, effects, position or motion runs alone. Keep the two stubs in
 * step - a batch name that exists on one loader only re-opens the bug on the other.
 */
@GameTestHolder("creator_arsenal")
@PrefixGameTestTemplate(false)
public class ArsenalNeoForgeGameTests {

    @GameTest(template = "empty")
    public void featureIsEnabled(GameTestHelper helper) {
        ArsenalGameTests.featureIsEnabled(helper);
    }

    @GameTest(template = "empty")
    public void weaponsCarryTheirRarityAndAttributes(GameTestHelper helper) {
        ArsenalGameTests.weaponsCarryTheirRarityAndAttributes(helper);
    }

    @GameTest(template = "empty")
    public void giveCommandHandsOutAllFourAndArrows(GameTestHelper helper) {
        ArsenalGameTests.giveCommandHandsOutAllFourAndArrows(helper);
    }

    @GameTest(template = "empty")
    public void giveCommandRejectsAnUnknownWeapon(GameTestHelper helper) {
        ArsenalGameTests.giveCommandRejectsAnUnknownWeapon(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "creator_arsenal_storm_charged")
    public void stormArrowChargedStrikesWithoutFire(GameTestHelper helper) {
        ArsenalGameTests.stormArrowChargedStrikesWithoutFire(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "creator_arsenal_storm_uncharged")
    public void stormArrowUnchargedNeverStrikes(GameTestHelper helper) {
        ArsenalGameTests.stormArrowUnchargedNeverStrikes(helper);
    }

    @GameTest(template = "empty", batch = "creator_arsenal_hammer_lift")
    public void hammerLiftsEverythingInRange(GameTestHelper helper) {
        ArsenalGameTests.hammerLiftsEverythingInRange(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "creator_arsenal_hammer_slam")
    public void hammerSlamsAfterTheHangTime(GameTestHelper helper) {
        ArsenalGameTests.hammerSlamsAfterTheHangTime(helper);
    }

    @GameTest(template = "empty", batch = "creator_arsenal_scythe_sweep")
    public void scytheHealsAndSweeps(GameTestHelper helper) {
        ArsenalGameTests.scytheHealsAndSweeps(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "creator_arsenal_scythe_wisp")
    public void scytheKillGrantsAbsorptionAfterTheWisp(GameTestHelper helper) {
        ArsenalGameTests.scytheKillGrantsAbsorptionAfterTheWisp(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "creator_arsenal_grapple_bite")
    public void grappleHookBitesAndReelsThePlayer(GameTestHelper helper) {
        ArsenalGameTests.grappleHookBitesAndReelsThePlayer(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "creator_arsenal_grapple_cut")
    public void grappleSecondUseCutsTheLine(GameTestHelper helper) {
        ArsenalGameTests.grappleSecondUseCutsTheLine(helper);
    }

    @GameTest(template = "empty", batch = "creator_arsenal_slam_window")
    public void slamDamageSurvivesTheFallDamageWindow(GameTestHelper helper) {
        ArsenalGameTests.slamDamageSurvivesTheFallDamageWindow(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "creator_arsenal_storm_blast")
    public void stormArrowBlastFullyHitsWhatItStruck(GameTestHelper helper) {
        ArsenalGameTests.stormArrowBlastFullyHitsWhatItStruck(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "creator_arsenal_scythe_vanilla")
    public void scytheDoesNotSweepOnTopOfVanillasSweep(GameTestHelper helper) {
        ArsenalGameTests.scytheDoesNotSweepOnTopOfVanillasSweep(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "creator_arsenal_grapple_reel")
    public void grappleReelCarriesThePlayerAcrossTheGap(GameTestHelper helper) {
        ArsenalGameTests.grappleReelCarriesThePlayerAcrossTheGap(helper);
    }
}
