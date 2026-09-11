package dev.riftal.creator.features.powers.gametest;

import dev.riftal.creator.features.powers.PowersFeature;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric discovery stub for the {@code powers} feature. Already listed in the {@code fabric-gametest}
 * entrypoint of {@code fabric/src/gametest/resources/fabric.mod.json} - do not edit that file.
 *
 * <p>Fabric uses {@code template()} verbatim, so a template must be written as a full
 * {@code namespace:path}. Structures live in
 * {@code common/src/main/resources/data/creator_powers/structure/}.
 */
public class PowersFabricGameTests implements FabricGameTest {

    private static final String EMPTY = PowersFeature.NAMESPACE + ":empty";

    @GameTest(template = EMPTY)
    public void featureIsEnabled(GameTestHelper helper) {
        PowersGameTests.featureIsEnabled(helper);
    }
}
