package dev.riftal.creator.features.colossus.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge discovery stub for the {@code colossus} feature. Auto-scanned because of
 * {@link GameTestHolder}; {@code creator_colossus} is already listed in
 * {@code neoforge.enabledGameTestNamespaces}.
 *
 * <p>{@link PrefixGameTestTemplate}(false) keeps the template id at {@code creator_mpty}
 * instead of {@code creator_colossus:Colossusneoforgegametests.empty}.
 */
@GameTestHolder("creator_colossus")
@PrefixGameTestTemplate(false)
public class ColossusNeoForgeGameTests {

    @GameTest(template = "empty")
    public void featureIsEnabled(GameTestHelper helper) {
        ColossusGameTests.featureIsEnabled(helper);
    }
}
