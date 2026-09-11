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

    @GameTest(template = "empty", timeoutTicks = 120)
    public void stormArrowChargedStrikesWithoutFire(GameTestHelper helper) {
        ArsenalGameTests.stormArrowChargedStrikesWithoutFire(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public void stormArrowUnchargedNeverStrikes(GameTestHelper helper) {
        ArsenalGameTests.stormArrowUnchargedNeverStrikes(helper);
    }

    @GameTest(template = "empty")
    public void hammerLiftsEverythingInRange(GameTestHelper helper) {
        ArsenalGameTests.hammerLiftsEverythingInRange(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public void hammerSlamsAfterTheHangTime(GameTestHelper helper) {
        ArsenalGameTests.hammerSlamsAfterTheHangTime(helper);
    }

    @GameTest(template = "empty")
    public void scytheHealsAndSweeps(GameTestHelper helper) {
        ArsenalGameTests.scytheHealsAndSweeps(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public void scytheKillGrantsAbsorptionAfterTheWisp(GameTestHelper helper) {
        ArsenalGameTests.scytheKillGrantsAbsorptionAfterTheWisp(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public void grappleHookBitesAndReelsThePlayer(GameTestHelper helper) {
        ArsenalGameTests.grappleHookBitesAndReelsThePlayer(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public void grappleSecondUseCutsTheLine(GameTestHelper helper) {
        ArsenalGameTests.grappleSecondUseCutsTheLine(helper);
    }
}
