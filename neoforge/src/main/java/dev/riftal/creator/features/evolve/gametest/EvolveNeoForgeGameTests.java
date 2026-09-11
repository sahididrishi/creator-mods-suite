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
}
