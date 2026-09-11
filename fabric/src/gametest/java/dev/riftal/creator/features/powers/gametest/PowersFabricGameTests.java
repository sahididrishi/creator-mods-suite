package dev.riftal.creator.features.powers.gametest;

import dev.riftal.creator.features.powers.PowersFeature;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric discovery stub for the {@code powers} feature. Already listed in the {@code fabric-gametest}
 * entrypoint of {@code fabric/src/gametest/resources/fabric.mod.json} - do not edit that file, and
 * never add a sibling test class: Fabric only ever loads the nine classes named there, so a new
 * class is silently never run.
 *
 * <p>Fabric uses {@code template()} verbatim, so a template must be written as a full
 * {@code namespace:path}. Structures live in
 * {@code common/src/main/resources/data/creator_powers/structure/}.
 *
 * <p><b>Every test that spawns a mob gets its own {@code batch}.</b> Tests inside one batch run
 * side by side in a row of arenas a few blocks apart, and this feature's abilities are area
 * effects: Mob Freeze reaches 12 blocks, Ender Pull ray-casts 20, Fire Burst 7 and the pound
 * shockwave 6. Sharing a batch would let one test freeze, pull, burn or launch the mobs another
 * test is asserting on - and the failure would look like a bug in the ability rather than in the
 * test layout.
 */
public class PowersFabricGameTests implements FabricGameTest {

    private static final String EMPTY = PowersFeature.NAMESPACE + ":empty";

    @GameTest(template = EMPTY)
    public void featureIsEnabled(GameTestHelper helper) {
        PowersGameTests.featureIsEnabled(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_powers_grant")
    public void grantingFillsSlotsInOrderAndClearingEmptiesThem(GameTestHelper helper) {
        PowersGameTests.grantingFillsSlotsInOrderAndClearingEmptiesThem(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 120, batch = "creator_powers_cooldown")
    public void useStartsACooldownAndIsRefusedUntilItElapses(GameTestHelper helper) {
        PowersGameTests.useStartsACooldownAndIsRefusedUntilItElapses(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_powers_dash")
    public void dashLaunchesThePlayerAndOpensAnIFrameWindow(GameTestHelper helper) {
        PowersGameTests.dashLaunchesThePlayerAndOpensAnIFrameWindow(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_powers_fire_burst")
    public void fireBurstBurnsOnlyWhatIsInTheCone(GameTestHelper helper) {
        PowersGameTests.fireBurstBurnsOnlyWhatIsInTheCone(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_powers_ender_pull")
    public void enderPullDragsTheTargetTowardsThePlayer(GameTestHelper helper) {
        PowersGameTests.enderPullDragsTheTargetTowardsThePlayer(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_powers_ender_pull_empty")
    public void enderPullWithNoTargetIsRefused(GameTestHelper helper) {
        PowersGameTests.enderPullWithNoTargetIsRefused(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "creator_powers_mob_freeze")
    public void mobFreezeHoldsHostilesAndThawsThem(GameTestHelper helper) {
        PowersGameTests.mobFreezeHoldsHostilesAndThawsThem(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_powers_ground_pound")
    public void groundPoundNeedsAirAndItsShockwaveThrowsMobs(GameTestHelper helper) {
        PowersGameTests.groundPoundNeedsAirAndItsShockwaveThrowsMobs(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 120, batch = "creator_powers_shield_dome")
    public void shieldDomeCoversThePlayerAndVoidsArrows(GameTestHelper helper) {
        PowersGameTests.shieldDomeCoversThePlayerAndVoidsArrows(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 300, batch = "creator_powers_dome_expiry")
    public void shieldDomeExpiresAndHandsBackTheBuffs(GameTestHelper helper) {
        PowersGameTests.shieldDomeExpiresAndHandsBackTheBuffs(helper);
    }
}
