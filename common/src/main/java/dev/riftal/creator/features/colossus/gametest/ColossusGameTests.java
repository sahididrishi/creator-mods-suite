package dev.riftal.creator.features.colossus.gametest;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.colossus.ColossusFeature;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * GameTest bodies for the {@code colossus} feature. Vanilla API only, so both loaders can call them.
 *
 * <p>Add a {@code public static void name(GameTestHelper helper)} here, then one annotated stub in
 * {@code fabric/src/gametest/java/.../ColossusFabricGameTests.java} and one in
 * {@code neoforge/src/main/java/.../ColossusNeoForgeGameTests.java}.
 */
public final class ColossusGameTests {

    /** Smoke test: the feature survived the config filter and is live in this session. */
    public static void featureIsEnabled(GameTestHelper helper) {
        helper.assertTrue(CreatorMods.isEnabled(ColossusFeature.ID),
                "feature '" + ColossusFeature.ID + "' should be enabled in the test session");
        helper.succeed();
    }

    private ColossusGameTests() {
    }
}
