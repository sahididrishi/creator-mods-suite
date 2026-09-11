package dev.riftal.creator.features.rules.gametest;

import dev.riftal.creator.features.rules.RulesFeature;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric discovery stub for the {@code rules} feature. Already listed in the {@code fabric-gametest}
 * entrypoint of {@code fabric/src/gametest/resources/fabric.mod.json} - do not edit that file, and
 * never add a sibling class: only methods on this one are discovered.
 *
 * <p>Fabric uses {@code template()} verbatim, so a template must be written as a full
 * {@code namespace:path}. Structures live in
 * {@code common/src/main/resources/data/creator_rules/structure/}.
 */
public class RulesFabricGameTests implements FabricGameTest {

    private static final String EMPTY = RulesFeature.NAMESPACE + ":empty";

    @GameTest(template = EMPTY)
    public void featureIsEnabled(GameTestHelper helper) {
        RulesGameTests.featureIsEnabled(helper);
    }

    @GameTest(template = EMPTY)
    public void gravityRuleScalesGravity(GameTestHelper helper) {
        RulesGameTests.gravityRuleScalesGravity(helper);
    }

    @GameTest(template = EMPTY)
    public void oneHeartCapsMaxHealth(GameTestHelper helper) {
        RulesGameTests.oneHeartCapsMaxHealth(helper);
    }

    @GameTest(template = EMPTY)
    public void giantMobsScalesAndRestoresMob(GameTestHelper helper) {
        RulesGameTests.giantMobsScalesAndRestoresMob(helper);
    }

    @GameTest(template = EMPTY)
    public void randomDropsMappingIsStableForAWorld(GameTestHelper helper) {
        RulesGameTests.randomDropsMappingIsStableForAWorld(helper);
    }

    @GameTest(template = EMPTY)
    public void craftsX10InsertsNineExtraStacks(GameTestHelper helper) {
        RulesGameTests.craftsX10InsertsNineExtraStacks(helper);
    }

    @GameTest(template = EMPTY)
    public void lavaFloorMeltsOnlyMeltableBlocks(GameTestHelper helper) {
        RulesGameTests.lavaFloorMeltsOnlyMeltableBlocks(helper);
    }

    @GameTest(template = EMPTY)
    public void inventoryShufflePermutesTheMainSlots(GameTestHelper helper) {
        RulesGameTests.inventoryShufflePermutesTheMainSlots(helper);
    }

    @GameTest(template = EMPTY)
    public void shippedPresetsLoadAndNameKnownRules(GameTestHelper helper) {
        RulesGameTests.shippedPresetsLoadAndNameKnownRules(helper);
    }

    @GameTest(template = EMPTY)
    public void ruleTagsAreLoadedFromTheDataPack(GameTestHelper helper) {
        RulesGameTests.ruleTagsAreLoadedFromTheDataPack(helper);
    }

    @GameTest(template = EMPTY)
    public void shopCatalogueResolvesRealItems(GameTestHelper helper) {
        RulesGameTests.shopCatalogueResolvesRealItems(helper);
    }

    @GameTest(template = EMPTY)
    public void randomDropsReplacesDropsButNeverInventsThem(GameTestHelper helper) {
        RulesGameTests.randomDropsReplacesDropsButNeverInventsThem(helper);
    }

    @GameTest(template = EMPTY)
    public void giantMobsDoesNotHealOnRegrowth(GameTestHelper helper) {
        RulesGameTests.giantMobsDoesNotHealOnRegrowth(helper);
    }

    @GameTest(template = EMPTY)
    public void blocksExplodeSparesTheBuildAndTheDrops(GameTestHelper helper) {
        RulesGameTests.blocksExplodeSparesTheBuildAndTheDrops(helper);
    }

    @GameTest(template = EMPTY)
    public void blocksExplodeCooldownSuppressesTheSecondBreak(GameTestHelper helper) {
        RulesGameTests.blocksExplodeCooldownSuppressesTheSecondBreak(helper);
    }

    @GameTest(template = EMPTY)
    public void presetApplyAndClearDriveTheEngine(GameTestHelper helper) {
        RulesGameTests.presetApplyAndClearDriveTheEngine(helper);
    }
}
