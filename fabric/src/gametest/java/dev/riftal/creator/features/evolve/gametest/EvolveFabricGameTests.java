package dev.riftal.creator.features.evolve.gametest;

import dev.riftal.creator.features.evolve.EvolveFeature;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric discovery stub for the {@code evolve} feature. Already listed in the {@code fabric-gametest}
 * entrypoint of {@code fabric/src/gametest/resources/fabric.mod.json} - do not edit that file.
 *
 * <p>Fabric uses {@code template()} verbatim, so a template must be written as a full
 * {@code namespace:path}. Structures live in
 * {@code common/src/main/resources/data/creator_evolve/structure/}.
 */
public class EvolveFabricGameTests implements FabricGameTest {

    private static final String EMPTY = EvolveFeature.NAMESPACE + ":empty";

    @GameTest(template = EMPTY)
    public void featureIsEnabled(GameTestHelper helper) {
        EvolveGameTests.featureIsEnabled(helper);
    }
}
