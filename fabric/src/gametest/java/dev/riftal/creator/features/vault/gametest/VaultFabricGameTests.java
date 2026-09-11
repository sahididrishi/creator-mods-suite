package dev.riftal.creator.features.vault.gametest;

import dev.riftal.creator.features.vault.VaultFeature;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric discovery stub for the {@code vault} feature. Already listed in the {@code fabric-gametest}
 * entrypoint of {@code fabric/src/gametest/resources/fabric.mod.json} - do not edit that file, and
 * never add a sibling test class: Fabric only ever loads the nine classes named there, so a new
 * class is silently never run.
 *
 * <p>Fabric uses {@code template()} verbatim, so a template must be written as a full
 * {@code namespace:path}. Structures live in
 * {@code common/src/main/resources/data/creator_vault/structure/}.
 *
 * <p>Every test that places an altar gets its own {@code batch} so no two of them run side by side
 * - see the {@link VaultGameTests} class javadoc for why arenas 14 blocks apart would otherwise
 * unseal each other's chests.
 */
public class VaultFabricGameTests implements FabricGameTest {

    private static final String EMPTY = VaultFeature.NAMESPACE + ":empty";

    @GameTest(template = EMPTY)
    public void featureIsEnabled(GameTestHelper helper) {
        VaultGameTests.featureIsEnabled(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_vault_sealed")
    public void altarStartsSealed(GameTestHelper helper) {
        VaultGameTests.altarStartsSealed(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "creator_vault_summon")
    public void keyChargesTheAltarAndSummonsTheKeeper(GameTestHelper helper) {
        VaultGameTests.keyChargesTheAltarAndSummonsTheKeeper(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_vault_key")
    public void rightClickWithKeyConsumesIt(GameTestHelper helper) {
        VaultGameTests.rightClickWithKeyConsumesIt(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_vault_wrong_item")
    public void wrongItemIsRejected(GameTestHelper helper) {
        VaultGameTests.wrongItemIsRejected(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_vault_second_key")
    public void secondKeyIsRefusedWhileCharging(GameTestHelper helper) {
        VaultGameTests.secondKeyIsRefusedWhileCharging(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 300, batch = "creator_vault_death")
    public void keeperDeathUnsealsTheChest(GameTestHelper helper) {
        VaultGameTests.keeperDeathUnsealsTheChest(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "creator_vault_persistence")
    public void keeperIsPersistentAndBoundToItsAltar(GameTestHelper helper) {
        VaultGameTests.keeperIsPersistentAndBoundToItsAltar(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_vault_reset")
    public void resetReSealsTheChest(GameTestHelper helper) {
        VaultGameTests.resetReSealsTheChest(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_vault_unbreakable")
    public void sealedChestIsUnbreakable(GameTestHelper helper) {
        VaultGameTests.sealedChestIsUnbreakable(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_vault_loot")
    public void unsealedChestCarriesTheVaultLootTable(GameTestHelper helper) {
        VaultGameTests.unsealedChestCarriesTheVaultLootTable(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_vault_scan")
    public void altarOnlyOpensChestsItRecordedWhenActivated(GameTestHelper helper) {
        VaultGameTests.altarOnlyOpensChestsItRecordedWhenActivated(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_vault_nbt")
    public void altarNbtSurvivesASaveAndLoad(GameTestHelper helper) {
        VaultGameTests.altarNbtSurvivesASaveAndLoad(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "creator_vault_adopt")
    public void adoptedKeeperStillUnsealsOnDeath(GameTestHelper helper) {
        VaultGameTests.adoptedKeeperStillUnsealsOnDeath(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_vault_lookup")
    public void nearestAltarLookupPicksTheCloserAltar(GameTestHelper helper) {
        VaultGameTests.nearestAltarLookupPicksTheCloserAltar(helper);
    }

    @GameTest(template = EMPTY, batch = "creator_vault_command")
    public void commandResetReArmsTheNearestAltar(GameTestHelper helper) {
        VaultGameTests.commandResetReArmsTheNearestAltar(helper);
    }
}
