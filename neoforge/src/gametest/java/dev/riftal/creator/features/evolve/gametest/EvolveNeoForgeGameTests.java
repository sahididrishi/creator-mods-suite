package dev.riftal.creator.features.evolve.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge discovery stub for the {@code evolve} feature. Auto-scanned because of
 * {@link GameTestHolder}; {@code creator_evolve} is already listed in
 * {@code neoforge.enabledGameTestNamespaces}.
 *
 * <p>{@link PrefixGameTestTemplate}(false) keeps the template id at {@code creator_evolve:empty}
 * instead of {@code creator_evolve:Evolveneoforgegametests.empty}.
 */
@GameTestHolder("creator_evolve")
@PrefixGameTestTemplate(false)
public class EvolveNeoForgeGameTests {

    @GameTest(template = "empty")
    public void featureIsEnabled(GameTestHelper helper) {
        EvolveGameTests.featureIsEnabled(helper);
    }

    @GameTest(template = "empty")
    public void brutePutsOnTheRightBody(GameTestHelper helper) {
        EvolveGameTests.brutePutsOnTheRightBody(helper);
    }

    @GameTest(template = "empty")
    public void titanGetsStepHeightAndJump(GameTestHelper helper) {
        EvolveGameTests.titanGetsStepHeightAndJump(helper);
    }

    @GameTest(template = "empty")
    public void transformLockStopsAndReleases(GameTestHelper helper) {
        EvolveGameTests.transformLockStopsAndReleases(helper);
    }

    @GameTest(template = "empty")
    public void attachmentRoundTrips(GameTestHelper helper) {
        EvolveGameTests.attachmentRoundTrips(helper);
    }

    @GameTest(template = "empty")
    public void apexBeastSpawnsWithItsAttributes(GameTestHelper helper) {
        EvolveGameTests.apexBeastSpawnsWithItsAttributes(helper);
    }

    @GameTest(template = "empty")
    public void everyStageResizesThePlayer(GameTestHelper helper) {
        EvolveGameTests.everyStageResizesThePlayer(helper);
    }

    @GameTest(template = "empty")
    public void shrinkingClampsHealthIntoTheNewMaximum(GameTestHelper helper) {
        EvolveGameTests.shrinkingClampsHealthIntoTheNewMaximum(helper);
    }

    @GameTest(template = "empty")
    public void registeredIdsMatchTheShippedAssets(GameTestHelper helper) {
        EvolveGameTests.registeredIdsMatchTheShippedAssets(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public void killXpAutoEvolvesAndTheSequenceLands(GameTestHelper helper) {
        EvolveGameTests.killXpAutoEvolvesAndTheSequenceLands(helper);
    }

    @GameTest(template = "empty")
    public void aKillWithNoKillerAwardsNothing(GameTestHelper helper) {
        EvolveGameTests.aKillWithNoKillerAwardsNothing(helper);
    }

    @GameTest(template = "empty")
    public void eatingBreadFeedsTheLadder(GameTestHelper helper) {
        EvolveGameTests.eatingBreadFeedsTheLadder(helper);
    }

    @GameTest(template = "empty")
    public void resetUndoesEverything(GameTestHelper helper) {
        EvolveGameTests.resetUndoesEverything(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public void multiStageJumpLandsOnApexWithoutSpawningABeast(GameTestHelper helper) {
        EvolveGameTests.multiStageJumpLandsOnApexWithoutSpawningABeast(helper);
    }
}
