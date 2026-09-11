package dev.riftal.creator.features.events.gametest;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.events.EventsFeature;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * GameTest bodies for the {@code events} feature. Vanilla API only, so both loaders can call them.
 *
 * <p>Add a {@code public static void name(GameTestHelper helper)} here, then one annotated stub in
 * {@code fabric/src/gametest/java/.../EventsFabricGameTests.java} and one in
 * {@code neoforge/src/main/java/.../EventsNeoForgeGameTests.java}.
 */
public final class EventsGameTests {

    /** Smoke test: the feature survived the config filter and is live in this session. */
    public static void featureIsEnabled(GameTestHelper helper) {
        helper.assertTrue(CreatorMods.isEnabled(EventsFeature.ID),
                "feature '" + EventsFeature.ID + "' should be enabled in the test session");
        helper.succeed();
    }

    private EventsGameTests() {
    }
}
