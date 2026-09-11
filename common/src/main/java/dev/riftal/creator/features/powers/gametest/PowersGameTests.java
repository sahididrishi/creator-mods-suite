package dev.riftal.creator.features.powers.gametest;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.powers.PowersFeature;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * GameTest bodies for the {@code powers} feature. Vanilla API only, so both loaders can call them.
 *
 * <p>Add a {@code public static void name(GameTestHelper helper)} here, then one annotated stub in
 * {@code fabric/src/gametest/java/.../PowersFabricGameTests.java} and one in
 * {@code neoforge/src/main/java/.../PowersNeoForgeGameTests.java}.
 */
public final class PowersGameTests {

    /** Smoke test: the feature survived the config filter and is live in this session. */
    public static void featureIsEnabled(GameTestHelper helper) {
        helper.assertTrue(CreatorMods.isEnabled(PowersFeature.ID),
                "feature '" + PowersFeature.ID + "' should be enabled in the test session");
        helper.succeed();
    }

    private PowersGameTests() {
    }
}
