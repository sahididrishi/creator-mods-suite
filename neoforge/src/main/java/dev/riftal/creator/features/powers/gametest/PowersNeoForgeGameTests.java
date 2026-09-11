package dev.riftal.creator.features.powers.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge discovery stub for the {@code powers} feature. Auto-scanned because of
 * {@link GameTestHolder}; {@code creator_powers} is already listed in
 * {@code neoforge.enabledGameTestNamespaces}.
 *
 * <p>{@link PrefixGameTestTemplate}(false) keeps the template id at {@code creator_powers:empty}
 * instead of {@code creator_powers:Powersneoforgegametests.empty}.
 */
@GameTestHolder("creator_powers")
@PrefixGameTestTemplate(false)
public class PowersNeoForgeGameTests {

    @GameTest(template = "empty")
    public void featureIsEnabled(GameTestHelper helper) {
        PowersGameTests.featureIsEnabled(helper);
    }
}
