package dev.riftal.creator.features.vault.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge discovery stub for the {@code vault} feature. Auto-scanned because of
 * {@link GameTestHolder}; {@code creator_vault} is already listed in
 * {@code neoforge.enabledGameTestNamespaces}.
 *
 * <p>{@link PrefixGameTestTemplate}(false) keeps the template id at {@code creator_vault:empty}
 * instead of {@code creator_vault:Vaultneoforgegametests.empty}.
 *
 * <p>The {@code batch} names mirror the Fabric stub: every test that places an altar runs alone,
 * because {@code StructureGridSpawner} puts neighbouring arenas 14 blocks apart and the altar's
 * chest scan reaches 16 - see the {@link VaultGameTests} class javadoc.
 */
@GameTestHolder("creator_vault")
@PrefixGameTestTemplate(false)
public class VaultNeoForgeGameTests {

    @GameTest(template = "empty")
    public void featureIsEnabled(GameTestHelper helper) {
        VaultGameTests.featureIsEnabled(helper);
    }

    @GameTest(template = "empty", batch = "creator_vault_sealed")
    public void altarStartsSealed(GameTestHelper helper) {
        VaultGameTests.altarStartsSealed(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "creator_vault_summon")
    public void keyChargesTheAltarAndSummonsTheKeeper(GameTestHelper helper) {
        VaultGameTests.keyChargesTheAltarAndSummonsTheKeeper(helper);
    }

    @GameTest(template = "empty", batch = "creator_vault_key")
    public void rightClickWithKeyConsumesIt(GameTestHelper helper) {
        VaultGameTests.rightClickWithKeyConsumesIt(helper);
    }

    @GameTest(template = "empty", batch = "creator_vault_wrong_item")
    public void wrongItemIsRejected(GameTestHelper helper) {
        VaultGameTests.wrongItemIsRejected(helper);
    }

    @GameTest(template = "empty", batch = "creator_vault_second_key")
    public void secondKeyIsRefusedWhileCharging(GameTestHelper helper) {
        VaultGameTests.secondKeyIsRefusedWhileCharging(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "creator_vault_death")
    public void keeperDeathUnsealsTheChest(GameTestHelper helper) {
        VaultGameTests.keeperDeathUnsealsTheChest(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "creator_vault_persistence")
    public void keeperIsPersistentAndBoundToItsAltar(GameTestHelper helper) {
        VaultGameTests.keeperIsPersistentAndBoundToItsAltar(helper);
    }

    @GameTest(template = "empty", batch = "creator_vault_reset")
    public void resetReSealsTheChest(GameTestHelper helper) {
        VaultGameTests.resetReSealsTheChest(helper);
    }

    @GameTest(template = "empty", batch = "creator_vault_unbreakable")
    public void sealedChestIsUnbreakable(GameTestHelper helper) {
        VaultGameTests.sealedChestIsUnbreakable(helper);
    }

    @GameTest(template = "empty", batch = "creator_vault_loot")
    public void unsealedChestCarriesTheVaultLootTable(GameTestHelper helper) {
        VaultGameTests.unsealedChestCarriesTheVaultLootTable(helper);
    }

    @GameTest(template = "empty", batch = "creator_vault_scan")
    public void altarOnlyOpensChestsItRecordedWhenActivated(GameTestHelper helper) {
        VaultGameTests.altarOnlyOpensChestsItRecordedWhenActivated(helper);
    }

    @GameTest(template = "empty", batch = "creator_vault_nbt")
    public void altarNbtSurvivesASaveAndLoad(GameTestHelper helper) {
        VaultGameTests.altarNbtSurvivesASaveAndLoad(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "creator_vault_adopt")
    public void adoptedKeeperStillUnsealsOnDeath(GameTestHelper helper) {
        VaultGameTests.adoptedKeeperStillUnsealsOnDeath(helper);
    }

    @GameTest(template = "empty", batch = "creator_vault_lookup")
    public void nearestAltarLookupPicksTheCloserAltar(GameTestHelper helper) {
        VaultGameTests.nearestAltarLookupPicksTheCloserAltar(helper);
    }

    @GameTest(template = "empty", batch = "creator_vault_command")
    public void commandResetReArmsTheNearestAltar(GameTestHelper helper) {
        VaultGameTests.commandResetReArmsTheNearestAltar(helper);
    }
}
