package dev.riftal.creator.gametest;

import dev.riftal.creator.Constants;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Registered through the {@code fabric-gametest} entrypoint in
 * {@code fabric/src/gametest/resources/fabric.mod.json}. Needs a public no-arg constructor.
 * <p>
 * Vanilla's {@code GameTest} annotation has no {@code templateNamespace} field (that is a NeoForge
 * addition), and Fabric's {@code TestFunctionsMixin} uses {@code template()} verbatim when it is not
 * empty - so the template must be written as a full {@code namespace:path} here.
 */
public class FabricGameTests implements FabricGameTest {

    private static final String EMPTY = Constants.MOD_ID + ":empty";

    @GameTest(template = EMPTY)
    public void harnessBoots(GameTestHelper helper) {
        CommonGameTests.harnessBoots(helper);
    }
}
