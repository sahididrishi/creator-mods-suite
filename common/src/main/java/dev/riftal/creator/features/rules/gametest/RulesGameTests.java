package dev.riftal.creator.features.rules.gametest;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.rules.RulesFeature;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * GameTest bodies for the {@code rules} feature. Vanilla API only, so both loaders can call them.
 *
 * <p>Add a {@code public static void name(GameTestHelper helper)} here, then one annotated stub in
 * {@code fabric/src/gametest/java/.../RulesFabricGameTests.java} and one in
 * {@code neoforge/src/main/java/.../RulesNeoForgeGameTests.java}.
 */
public final class RulesGameTests {

    /** Smoke test: the feature survived the config filter and is live in this session. */
    public static void featureIsEnabled(GameTestHelper helper) {
        helper.assertTrue(CreatorMods.isEnabled(RulesFeature.ID),
                "feature '" + RulesFeature.ID + "' should be enabled in the test session");
        helper.succeed();
    }

    private RulesGameTests() {
    }
}
