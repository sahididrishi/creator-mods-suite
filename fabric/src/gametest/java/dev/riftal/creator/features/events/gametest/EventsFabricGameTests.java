package dev.riftal.creator.features.events.gametest;

import dev.riftal.creator.features.events.EventsFeature;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric discovery stub for the {@code events} feature. Already listed in the {@code fabric-gametest}
 * entrypoint of {@code fabric/src/gametest/resources/fabric.mod.json} - do not edit that file.
 *
 * <p>Fabric uses {@code template()} verbatim, so a template must be written as a full
 * {@code namespace:path}. Structures live in
 * {@code common/src/main/resources/data/creator_events/structure/}.
 */
public class EventsFabricGameTests implements FabricGameTest {

    private static final String EMPTY = EventsFeature.NAMESPACE + ":empty";

    @GameTest(template = EMPTY)
    public void featureIsEnabled(GameTestHelper helper) {
        EventsGameTests.featureIsEnabled(helper);
    }
}
