package dev.riftal.creator.features.toolkit.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge discovery stub for the {@code toolkit} feature. Auto-scanned because of
 * {@link GameTestHolder}; {@code creator_toolkit} is already listed in
 * {@code neoforge.enabledGameTestNamespaces}.
 *
 * <p>{@link PrefixGameTestTemplate}(false) keeps the template id at {@code creator_mpty}
 * instead of {@code creator_toolkit:Toolkitneoforgegametests.empty}.
 */
@GameTestHolder("creator_toolkit")
@PrefixGameTestTemplate(false)
public class ToolkitNeoForgeGameTests {

    @GameTest(template = "empty")
    public void featureIsEnabled(GameTestHelper helper) {
        ToolkitGameTests.featureIsEnabled(helper);
    }
}
