package dev.riftal.creator.features.rules.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge discovery stub for the {@code rules} feature. Auto-scanned because of
 * {@link GameTestHolder}; {@code creator_rules} is already listed in
 * {@code neoforge.enabledGameTestNamespaces}.
 *
 * <p>{@link PrefixGameTestTemplate}(false) keeps the template id at {@code creator_rules:empty}
 * instead of {@code creator_rules:Rulesneoforgegametests.empty}.
 */
@GameTestHolder("creator_rules")
@PrefixGameTestTemplate(false)
public class RulesNeoForgeGameTests {

    @GameTest(template = "empty")
    public void featureIsEnabled(GameTestHelper helper) {
        RulesGameTests.featureIsEnabled(helper);
    }
}
