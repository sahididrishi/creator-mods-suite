package dev.riftal.creator.features.rules.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge discovery stub for the {@code rules} feature. Auto-scanned because of
 * {@link GameTestHolder}; {@code creator_rules} is already listed in
 * {@code neoforge.enabledGameTestNamespaces}.
 *
 * <p>{@link PrefixGameTestTemplate}(false) keeps the template id at {@code creator_rules:empty}
 * instead of {@code creator_rules:Rulesneoforgegametests.empty}.
 */
@GameTestHolder("creator_rules")
@PrefixGameTestTemplate(false)
public class RulesNeoForgeGameTests {

    @GameTest(template = "empty")
    public void featureIsEnabled(GameTestHelper helper) {
        RulesGameTests.featureIsEnabled(helper);
    }

    @GameTest(template = "empty")
    public void gravityRuleScalesGravity(GameTestHelper helper) {
        RulesGameTests.gravityRuleScalesGravity(helper);
    }

    @GameTest(template = "empty")
    public void oneHeartCapsMaxHealth(GameTestHelper helper) {
        RulesGameTests.oneHeartCapsMaxHealth(helper);
    }

    @GameTest(template = "empty")
    public void giantMobsScalesAndRestoresMob(GameTestHelper helper) {
        RulesGameTests.giantMobsScalesAndRestoresMob(helper);
    }

    @GameTest(template = "empty")
    public void randomDropsMappingIsStableForAWorld(GameTestHelper helper) {
        RulesGameTests.randomDropsMappingIsStableForAWorld(helper);
    }

    @GameTest(template = "empty")
    public void craftsX10InsertsNineExtraStacks(GameTestHelper helper) {
        RulesGameTests.craftsX10InsertsNineExtraStacks(helper);
    }

    @GameTest(template = "empty")
    public void lavaFloorMeltsOnlyMeltableBlocks(GameTestHelper helper) {
        RulesGameTests.lavaFloorMeltsOnlyMeltableBlocks(helper);
    }

    @GameTest(template = "empty")
    public void blocksExplodeLeavesTheBuildStanding(GameTestHelper helper) {
        RulesGameTests.blocksExplodeLeavesTheBuildStanding(helper);
    }

    @GameTest(template = "empty")
    public void inventoryShufflePermutesTheMainSlots(GameTestHelper helper) {
        RulesGameTests.inventoryShufflePermutesTheMainSlots(helper);
    }

    @GameTest(template = "empty")
    public void shippedPresetsLoadAndNameKnownRules(GameTestHelper helper) {
        RulesGameTests.shippedPresetsLoadAndNameKnownRules(helper);
    }

    @GameTest(template = "empty")
    public void ruleTagsAreLoadedFromTheDataPack(GameTestHelper helper) {
        RulesGameTests.ruleTagsAreLoadedFromTheDataPack(helper);
    }

    @GameTest(template = "empty")
    public void shopCatalogueResolvesRealItems(GameTestHelper helper) {
        RulesGameTests.shopCatalogueResolvesRealItems(helper);
    }

    @GameTest(template = "empty")
    public void randomDropsReplacesDropsButNeverInventsThem(GameTestHelper helper) {
        RulesGameTests.randomDropsReplacesDropsButNeverInventsThem(helper);
    }
}
