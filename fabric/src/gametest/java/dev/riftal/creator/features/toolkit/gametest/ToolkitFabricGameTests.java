package dev.riftal.creator.features.toolkit.gametest;

import dev.riftal.creator.features.toolkit.ToolkitFeature;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric discovery stub for the {@code toolkit} feature. Already listed in the {@code fabric-gametest}
 * entrypoint of {@code fabric/src/gametest/resources/fabric.mod.json} - do not edit that file, and
 * never move these tests to a sibling class: Fabric would silently never run them.
 *
 * <p>Fabric uses {@code template()} verbatim, so a template must be written as a full
 * {@code namespace:path}. Structures live in
 * {@code common/src/main/resources/data/creator_toolkit/structure/}.
 */
public class ToolkitFabricGameTests implements FabricGameTest {

    private static final String EMPTY = ToolkitFeature.NAMESPACE + ":empty";

    @GameTest(template = EMPTY)
    public void featureIsEnabled(GameTestHelper helper) {
        ToolkitGameTests.featureIsEnabled(helper);
    }

    @GameTest(template = EMPTY)
    public void waveRingSpawnsExactCount(GameTestHelper helper) {
        ToolkitGameTests.waveRingSpawnsExactCount(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 160)
    public void freezeMobsStopsMovement(GameTestHelper helper) {
        ToolkitGameTests.freezeMobsStopsMovement(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public void arenaResetRestoresBlocksAndEntities(GameTestHelper helper) {
        ToolkitGameTests.arenaResetRestoresBlocksAndEntities(helper);
    }

    @GameTest(template = EMPTY)
    public void arenaSaveRejectsOversizeVolumes(GameTestHelper helper) {
        ToolkitGameTests.arenaSaveRejectsOversizeVolumes(helper);
    }

    @GameTest(template = EMPTY)
    public void takeRecordsMarks(GameTestHelper helper) {
        ToolkitGameTests.takeRecordsMarks(helper);
    }

    @GameTest(template = EMPTY)
    public void cameraBookmarksRoundTripThroughSavedState(GameTestHelper helper) {
        ToolkitGameTests.cameraBookmarksRoundTripThroughSavedState(helper);
    }

    @GameTest(template = EMPTY)
    public void cheatGodBlocksDamage(GameTestHelper helper) {
        ToolkitGameTests.cheatGodBlocksDamage(helper);
    }

    @GameTest(template = EMPTY)
    public void tpHereSetsExactRotation(GameTestHelper helper) {
        ToolkitGameTests.tpHereSetsExactRotation(helper);
    }

    @GameTest(template = EMPTY)
    public void silentModeFlipsGamerules(GameTestHelper helper) {
        ToolkitGameTests.silentModeFlipsGamerules(helper);
    }

    @GameTest(template = EMPTY)
    public void hideNametagsTeamsTheCrewForVanillaClients(GameTestHelper helper) {
        ToolkitGameTests.hideNametagsTeamsTheCrewForVanillaClients(helper);
    }
}
