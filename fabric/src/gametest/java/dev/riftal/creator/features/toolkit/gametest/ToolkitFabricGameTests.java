package dev.riftal.creator.features.toolkit.gametest;

import dev.riftal.creator.features.toolkit.ToolkitFeature;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric discovery stub for the {@code toolkit} feature. Already listed in the {@code fabric-gametest}
 * entrypoint of {@code fabric/src/gametest/resources/fabric.mod.json} - do not edit that file.
 *
 * <p>Fabric uses {@code template()} verbatim, so a template must be written as a full
 * {@code namespace:path}. Structures live in
 * {@code common/src/main/resources/data/creator_toolkit/structure/}.
 */
public class ToolkitFabricGameTests implements FabricGameTest {

    private static final String EMPTY = ToolkitFeature.NAMESPACE + ":empty";

    @GameTest(template = EMPTY)
    public void featureIsEnabled(GameTestHelper helper) {
        ToolkitGameTests.featureIsEnabled(helper);
    }
}
