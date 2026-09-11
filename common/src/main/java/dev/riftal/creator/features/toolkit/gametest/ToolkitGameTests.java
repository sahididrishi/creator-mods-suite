package dev.riftal.creator.features.toolkit.gametest;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * GameTest bodies for the {@code toolkit} feature. Vanilla API only, so both loaders can call them.
 *
 * <p>Add a {@code public static void name(GameTestHelper helper)} here, then one annotated stub in
 * {@code fabric/src/gametest/java/.../ToolkitFabricGameTests.java} and one in
 * {@code neoforge/src/main/java/.../ToolkitNeoForgeGameTests.java}.
 */
public final class ToolkitGameTests {

    /** Smoke test: the feature survived the config filter and is live in this session. */
    public static void featureIsEnabled(GameTestHelper helper) {
        helper.assertTrue(CreatorMods.isEnabled(ToolkitFeature.ID),
                "feature '" + ToolkitFeature.ID + "' should be enabled in the test session");
        helper.succeed();
    }

    private ToolkitGameTests() {
    }
}
