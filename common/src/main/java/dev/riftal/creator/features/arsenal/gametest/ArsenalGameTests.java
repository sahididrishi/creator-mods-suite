package dev.riftal.creator.features.arsenal.gametest;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.arsenal.ArsenalFeature;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * GameTest bodies for the {@code arsenal} feature. Vanilla API only, so both loaders can call them.
 *
 * <p>Add a {@code public static void name(GameTestHelper helper)} here, then one annotated stub in
 * {@code fabric/src/gametest/java/.../ArsenalFabricGameTests.java} and one in
 * {@code neoforge/src/main/java/.../ArsenalNeoForgeGameTests.java}.
 */
public final class ArsenalGameTests {

    /** Smoke test: the feature survived the config filter and is live in this session. */
    public static void featureIsEnabled(GameTestHelper helper) {
        helper.assertTrue(CreatorMods.isEnabled(ArsenalFeature.ID),
                "feature '" + ArsenalFeature.ID + "' should be enabled in the test session");
        helper.succeed();
    }

    private ArsenalGameTests() {
    }
}
