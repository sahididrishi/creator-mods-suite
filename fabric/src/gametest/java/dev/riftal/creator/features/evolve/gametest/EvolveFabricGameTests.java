package dev.riftal.creator.features.evolve.gametest;

import dev.riftal.creator.features.evolve.EvolveFeature;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric discovery stub for the {@code evolve} feature. Already listed in the {@code fabric-gametest}
 * entrypoint of {@code fabric/src/gametest/resources/fabric.mod.json} - do not edit that file, and
 * never move these methods to a sibling class: a class that is not in that entrypoint list is
 * silently never run.
 *
 * <p>Fabric uses {@code template()} verbatim, so a template must be written as a full
 * {@code namespace:path}. Structures live in
 * {@code common/src/main/resources/data/creator_evolve/structure/}.
 */
public class EvolveFabricGameTests implements FabricGameTest {

    private static final String EMPTY = EvolveFeature.NAMESPACE + ":empty";

    @GameTest(template = EMPTY)
    public void featureIsEnabled(GameTestHelper helper) {
        EvolveGameTests.featureIsEnabled(helper);
    }

    @GameTest(template = EMPTY)
    public void brutePutsOnTheRightBody(GameTestHelper helper) {
        EvolveGameTests.brutePutsOnTheRightBody(helper);
    }

    @GameTest(template = EMPTY)
    public void titanGetsStepHeightAndJump(GameTestHelper helper) {
        EvolveGameTests.titanGetsStepHeightAndJump(helper);
    }

    @GameTest(template = EMPTY)
    public void transformLockStopsAndReleases(GameTestHelper helper) {
        EvolveGameTests.transformLockStopsAndReleases(helper);
    }

    @GameTest(template = EMPTY)
    public void attachmentRoundTrips(GameTestHelper helper) {
        EvolveGameTests.attachmentRoundTrips(helper);
    }

    @GameTest(template = EMPTY)
    public void apexBeastSpawnsWithItsAttributes(GameTestHelper helper) {
        EvolveGameTests.apexBeastSpawnsWithItsAttributes(helper);
    }

    @GameTest(template = EMPTY)
    public void everyStageResizesThePlayer(GameTestHelper helper) {
        EvolveGameTests.everyStageResizesThePlayer(helper);
    }

    @GameTest(template = EMPTY)
    public void shrinkingClampsHealthIntoTheNewMaximum(GameTestHelper helper) {
        EvolveGameTests.shrinkingClampsHealthIntoTheNewMaximum(helper);
    }

    @GameTest(template = EMPTY)
    public void registeredIdsMatchTheShippedAssets(GameTestHelper helper) {
        EvolveGameTests.registeredIdsMatchTheShippedAssets(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public void killXpAutoEvolvesAndTheSequenceLands(GameTestHelper helper) {
        EvolveGameTests.killXpAutoEvolvesAndTheSequenceLands(helper);
    }

    @GameTest(template = EMPTY)
    public void aKillWithNoKillerAwardsNothing(GameTestHelper helper) {
        EvolveGameTests.aKillWithNoKillerAwardsNothing(helper);
    }

    @GameTest(template = EMPTY)
    public void eatingBreadFeedsTheLadder(GameTestHelper helper) {
        EvolveGameTests.eatingBreadFeedsTheLadder(helper);
    }

    @GameTest(template = EMPTY)
    public void resetUndoesEverything(GameTestHelper helper) {
        EvolveGameTests.resetUndoesEverything(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public void multiStageJumpLandsOnApexWithoutSpawningABeast(GameTestHelper helper) {
        EvolveGameTests.multiStageJumpLandsOnApexWithoutSpawningABeast(helper);
    }
}
