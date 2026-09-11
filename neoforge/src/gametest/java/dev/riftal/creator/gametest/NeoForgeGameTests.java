package dev.riftal.creator.gametest;

import dev.riftal.creator.Constants;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Discovered by NeoForge because of {@link GameTestHolder}. {@link PrefixGameTestTemplate}(false)
 * keeps the template id at {@code creatormods:empty} instead of
 * {@code creatormods:neoforgegametests.empty}.
 * <p>
 * GameTests can never activate in production - {@code GameTestHooks} requires a non-production
 * loader - so shipping this class in the jar is harmless.
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class NeoForgeGameTests {

    @GameTest(template = "empty")
    public void harnessBoots(GameTestHelper helper) {
        CommonGameTests.harnessBoots(helper);
    }
}
