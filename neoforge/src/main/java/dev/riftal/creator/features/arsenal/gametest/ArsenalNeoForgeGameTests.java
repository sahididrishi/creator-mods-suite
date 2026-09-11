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
 * instead of {@code creator_arsenal:Arsenalneoforgegametests.empty}.
 */
@GameTestHolder("creator_arsenal")
@PrefixGameTestTemplate(false)
public class ArsenalNeoForgeGameTests {

    @GameTest(template = "empty")
    public void featureIsEnabled(GameTestHelper helper) {
        ArsenalGameTests.featureIsEnabled(helper);
    }
}
