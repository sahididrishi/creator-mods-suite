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
 * <p>{@link PrefixGameTestTemplate}(false) keeps the template id at {@code creator_mpty}
 * instead of {@code creator_vault:Vaultneoforgegametests.empty}.
 */
@GameTestHolder("creator_vault")
@PrefixGameTestTemplate(false)
public class VaultNeoForgeGameTests {

    @GameTest(template = "empty")
    public void featureIsEnabled(GameTestHelper helper) {
        VaultGameTests.featureIsEnabled(helper);
    }
}
