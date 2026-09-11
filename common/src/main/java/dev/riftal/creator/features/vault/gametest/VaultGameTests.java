package dev.riftal.creator.features.vault.gametest;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.vault.VaultFeature;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * GameTest bodies for the {@code vault} feature. Vanilla API only, so both loaders can call them.
 *
 * <p>Add a {@code public static void name(GameTestHelper helper)} here, then one annotated stub in
 * {@code fabric/src/gametest/java/.../VaultFabricGameTests.java} and one in
 * {@code neoforge/src/main/java/.../VaultNeoForgeGameTests.java}.
 */
public final class VaultGameTests {

    /** Smoke test: the feature survived the config filter and is live in this session. */
    public static void featureIsEnabled(GameTestHelper helper) {
        helper.assertTrue(CreatorMods.isEnabled(VaultFeature.ID),
                "feature '" + VaultFeature.ID + "' should be enabled in the test session");
        helper.succeed();
    }

    private VaultGameTests() {
    }
}
