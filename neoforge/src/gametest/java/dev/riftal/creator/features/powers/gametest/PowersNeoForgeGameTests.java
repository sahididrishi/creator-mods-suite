package dev.riftal.creator.features.powers.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge discovery stub for the {@code powers} feature. Auto-scanned because of
 * {@link GameTestHolder}; {@code creator_powers} is already listed in
 * {@code neoforge.enabledGameTestNamespaces}.
 *
 * <p>{@link PrefixGameTestTemplate}(false) keeps the template id at {@code creator_powers:empty}
 * instead of {@code creator_powers:Powersneoforgegametests.empty}.
 *
 * <p>The {@code batch} names match the Fabric stub one for one, and for the same reason: this
 * feature's abilities are area effects (freeze 12 blocks, pull 20, burst 7, pound 6), so two tests
 * running side by side in adjacent arenas would reach into each other's mobs.
 */
@GameTestHolder("creator_powers")
@PrefixGameTestTemplate(false)
public class PowersNeoForgeGameTests {

    @GameTest(template = "empty")
    public void featureIsEnabled(GameTestHelper helper) {
        PowersGameTests.featureIsEnabled(helper);
    }

    @GameTest(template = "empty", batch = "creator_powers_grant")
    public void grantingFillsSlotsInOrderAndClearingEmptiesThem(GameTestHelper helper) {
        PowersGameTests.grantingFillsSlotsInOrderAndClearingEmptiesThem(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "creator_powers_cooldown")
    public void useStartsACooldownAndIsRefusedUntilItElapses(GameTestHelper helper) {
        PowersGameTests.useStartsACooldownAndIsRefusedUntilItElapses(helper);
    }

    @GameTest(template = "empty", batch = "creator_powers_dash")
    public void dashLaunchesThePlayerAndOpensAnIFrameWindow(GameTestHelper helper) {
        PowersGameTests.dashLaunchesThePlayerAndOpensAnIFrameWindow(helper);
    }

    @GameTest(template = "empty", batch = "creator_powers_fire_burst")
    public void fireBurstBurnsOnlyWhatIsInTheCone(GameTestHelper helper) {
        PowersGameTests.fireBurstBurnsOnlyWhatIsInTheCone(helper);
    }

    @GameTest(template = "empty", batch = "creator_powers_ender_pull")
    public void enderPullDragsTheTargetTowardsThePlayer(GameTestHelper helper) {
        PowersGameTests.enderPullDragsTheTargetTowardsThePlayer(helper);
    }

    @GameTest(template = "empty", batch = "creator_powers_ender_pull_empty")
    public void enderPullWithNoTargetIsRefused(GameTestHelper helper) {
        PowersGameTests.enderPullWithNoTargetIsRefused(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "creator_powers_mob_freeze")
    public void mobFreezeHoldsHostilesAndThawsThem(GameTestHelper helper) {
        PowersGameTests.mobFreezeHoldsHostilesAndThawsThem(helper);
    }

    @GameTest(template = "empty", batch = "creator_powers_ground_pound")
    public void groundPoundNeedsAirAndItsShockwaveThrowsMobs(GameTestHelper helper) {
        PowersGameTests.groundPoundNeedsAirAndItsShockwaveThrowsMobs(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "creator_powers_shield_dome")
    public void shieldDomeCoversThePlayerAndVoidsArrows(GameTestHelper helper) {
        PowersGameTests.shieldDomeCoversThePlayerAndVoidsArrows(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "creator_powers_dome_expiry")
    public void shieldDomeExpiresAndHandsBackTheBuffs(GameTestHelper helper) {
        PowersGameTests.shieldDomeExpiresAndHandsBackTheBuffs(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "creator_powers_reload")
    public void reloadKeepsTheDomeAndTheWipeHandsItBack(GameTestHelper helper) {
        PowersGameTests.reloadKeepsTheDomeAndTheWipeHandsItBack(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "creator_powers_logout")
    public void logoutEndsTheDomeAndThawsThatPlayersMobs(GameTestHelper helper) {
        PowersGameTests.logoutEndsTheDomeAndThawsThatPlayersMobs(helper);
    }

    @GameTest(template = "empty", batch = "creator_powers_pound_filter")
    public void groundPoundSparesArmourStandsAndTheCastersOwnPet(GameTestHelper helper) {
        PowersGameTests.groundPoundSparesArmourStandsAndTheCastersOwnPet(helper);
    }
}
