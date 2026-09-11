package dev.riftal.creator.features.rules.gametest;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.features.rules.RuleIds;
import dev.riftal.creator.features.rules.RuleManager;
import dev.riftal.creator.features.rules.RuleTags;
import dev.riftal.creator.features.rules.RulesFeature;
import dev.riftal.creator.features.rules.api.RuleContext;
import dev.riftal.creator.features.rules.api.RuleRegistry;
import dev.riftal.creator.features.rules.preset.RulePreset;
import dev.riftal.creator.features.rules.preset.RulePresets;
import dev.riftal.creator.features.rules.rules.BlocksExplodeRule;
import dev.riftal.creator.features.rules.rules.CraftsX10Rule;
import dev.riftal.creator.features.rules.rules.GiantMobsRule;
import dev.riftal.creator.features.rules.rules.GravityX3Rule;
import dev.riftal.creator.features.rules.rules.InventoryShuffleRule;
import dev.riftal.creator.features.rules.rules.LavaFloorRule;
import dev.riftal.creator.features.rules.rules.OneHeartRule;
import dev.riftal.creator.features.rules.rules.RandomDropsMapping;
import dev.riftal.creator.features.rules.rules.RandomDropsRule;
import dev.riftal.creator.features.rules.rules.RandomItems;
import dev.riftal.creator.features.rules.shop.ShopOffer;
import dev.riftal.creator.features.rules.shop.ShopOffers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GameTest bodies for the {@code rules} feature. Vanilla API only, so both loaders can call them.
 *
 * <p>Each test drives a rule's own helpers directly rather than going through {@code RuleManager},
 * so a test never leaves a rule switched on for the rest of the run - GameTests share one server
 * with every other feature's tests.
 */
public final class RulesGameTests {

    /** Smoke test: the feature survived the config filter and is live in this session. */
    public static void featureIsEnabled(GameTestHelper helper) {
        helper.assertTrue(CreatorMods.isEnabled(RulesFeature.ID),
                "feature '" + RulesFeature.ID + "' should be enabled in the test session");
        helper.succeed();
    }

    /** {@code gravity_x3} triples gravity and lowers the safe fall distance, and undoes both. */
    public static void gravityRuleScalesGravity(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        double baseGravity = player.getAttributeValue(Attributes.GRAVITY);
        double baseFall = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);

        GravityX3Rule.applyTo(player);
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.GRAVITY) - baseGravity * 3.0D) < 1.0E-6D,
                "gravity should be tripled, was " + player.getAttributeValue(Attributes.GRAVITY));
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) - (baseFall - 1.0D)) < 1.0E-6D,
                "safe fall distance should drop by one");

        GravityX3Rule.removeFrom(player);
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.GRAVITY) - baseGravity) < 1.0E-6D,
                "gravity should be back to normal after the rule is switched off");
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) - baseFall) < 1.0E-6D,
                "safe fall distance should be back to normal");
        helper.succeed();
    }

    /** {@code one_heart} caps max health at one heart and pulls current health inside it. */
    public static void oneHeartCapsMaxHealth(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        float baseMax = player.getMaxHealth();

        OneHeartRule.applyTo(player);
        helper.assertTrue(player.getMaxHealth() == 2.0F,
                "max health should be 2, was " + player.getMaxHealth());
        helper.assertTrue(player.getHealth() <= 2.0F,
                "current health should have been clamped, was " + player.getHealth());

        OneHeartRule.removeFrom(player);
        helper.assertTrue(player.getMaxHealth() == baseMax,
                "max health should be restored, was " + player.getMaxHealth());
        helper.succeed();
    }

    /** {@code giant_mobs} scales a spawned mob and puts it back exactly as it was. */
    public static void giantMobsScalesAndRestoresMob(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        double baseScale = zombie.getAttributeValue(Attributes.SCALE);

        GiantMobsRule.grow(zombie);
        helper.assertTrue(Math.abs(zombie.getAttributeValue(Attributes.SCALE) - (baseScale + 2.0D)) < 1.0E-6D,
                "scale should be 3, was " + zombie.getAttributeValue(Attributes.SCALE));
        helper.assertTrue(zombie.getTags().contains(RuleIds.GIANT_TAG),
                "a grown mob should carry the giant tag so the sweep is idempotent");

        GiantMobsRule.shrink(zombie);
        helper.assertTrue(Math.abs(zombie.getAttributeValue(Attributes.SCALE) - baseScale) < 1.0E-6D,
                "scale should be back to normal, was " + zombie.getAttributeValue(Attributes.SCALE));
        helper.assertTrue(!zombie.getTags().contains(RuleIds.GIANT_TAG),
                "the giant tag should be gone");
        helper.assertTrue(zombie.getHealth() <= zombie.getMaxHealth(),
                "health should have been clamped back inside the smaller maximum");

        helper.killAllEntities();
        helper.succeed();
    }

    /**
     * A giant mob that has been fought down stays fought down when the rule re-applies its
     * modifiers.
     *
     * <p>Both modifiers are transient, so a chunk cycle strips them and the sweep grows the mob
     * again. That second growth used to call {@code heal}, which meant a giant zombie on its last
     * heart was back at full health every time its chunk reloaded. The persisted
     * {@code creator_rules_giant} tag is what tells the two cases apart.
     */
    public static void giantMobsDoesNotHealOnRegrowth(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));

        GiantMobsRule.grow(zombie);
        helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(),
                "the first growth should top a fresh mob up to its new maximum");

        zombie.setHealth(5.0F);
        // Exactly what a chunk reload looks like from the rule's side: modifiers gone, tag kept.
        GiantMobsRule.shrink(zombie);
        zombie.addTag(RuleIds.GIANT_TAG);
        zombie.setHealth(5.0F);
        GiantMobsRule.grow(zombie);

        helper.assertTrue(zombie.getHealth() == 5.0F,
                "a re-grown mob must keep the damage it has taken, was " + zombie.getHealth());
        helper.assertTrue(zombie.getMaxHealth() > 5.0F, "and it must be giant again");

        GiantMobsRule.shrink(zombie);
        helper.killAllEntities();
        helper.succeed();
    }

    /** {@code random_drops} produces the same mapping every time for the same world. */
    public static void randomDropsMappingIsStableForAWorld(GameTestHelper helper) {
        RuleContext context = new RuleContext(helper.getLevel().getServer());

        RandomDropsMapping first = new RandomDropsRule().mappingFor(context);
        RandomDropsMapping second = new RandomDropsRule().mappingFor(context);

        helper.assertTrue(first.size() > 0, "the mapping should not be empty");
        helper.assertTrue(first.asMap().equals(second.asMap()),
                "two mappings built for the same world must be identical");

        String stone = RandomDropsMapping.blockKey("minecraft:stone");
        helper.assertTrue(first.targetFor(stone) != null,
                "stone should be mapped to something");
        helper.assertTrue(!"minecraft:air".equals(first.targetFor(stone)),
                "stone must never be mapped to air");
        helper.succeed();
    }

    /** {@code crafts_x10} pushes exactly nine extra copies of the result into the inventory. */
    public static void craftsX10InsertsNineExtraStacks(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        CraftsX10Rule.multiply(player, new ItemStack(Items.STICK, 4));

        int sticks = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.STICK)) {
                sticks += stack.getCount();
            }
        }
        helper.assertTrue(sticks == 36,
                "expected 36 extra sticks (9 x 4), found " + sticks);
        helper.succeed();
    }

    /**
     * {@code lava_floor} melts an ordinary floor, and refuses the three cases that would ruin a set:
     * air, a fluid and anything in {@code #creator_rules:lava_floor_immune}.
     */
    public static void lavaFloorMeltsOnlyMeltableBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos floor = new BlockPos(1, 1, 1);
        helper.setBlock(floor, Blocks.STONE);
        helper.assertTrue(LavaFloorRule.melt(level, helper.absolutePos(floor)),
                "a plain stone floor should melt");
        helper.assertBlockPresent(Blocks.LAVA, floor);

        BlockPos immune = new BlockPos(3, 1, 1);
        helper.setBlock(immune, Blocks.OBSIDIAN);
        helper.assertFalse(LavaFloorRule.melt(level, helper.absolutePos(immune)),
                "obsidian is in #creator_rules:lava_floor_immune and must survive");
        helper.assertBlockPresent(Blocks.OBSIDIAN, immune);

        BlockPos unbreakable = new BlockPos(5, 1, 1);
        helper.setBlock(unbreakable, Blocks.BEDROCK);
        helper.assertFalse(LavaFloorRule.melt(level, helper.absolutePos(unbreakable)),
                "bedrock must never be turned into lava");
        helper.assertBlockPresent(Blocks.BEDROCK, unbreakable);

        BlockPos empty = new BlockPos(7, 1, 1);
        helper.setBlock(empty, Blocks.AIR);
        helper.assertFalse(LavaFloorRule.melt(level, helper.absolutePos(empty)),
                "air is not a floor and must not become lava");

        helper.assertFalse(LavaFloorRule.melt(level, helper.absolutePos(floor)),
                "a block that is already lava must not be melted again");

        helper.setBlock(floor, Blocks.AIR);
        helper.setBlock(immune, Blocks.AIR);
        helper.setBlock(unbreakable, Blocks.AIR);
        helper.succeed();
    }

    /**
     * {@code blocks_explode} fires a real blast that leaves the build standing <em>and</em> leaves
     * the drop the break just made lying there.
     *
     * <p>The second half is the one that matters: {@code ExplosionInteraction.NONE} only spares
     * blocks. Vanilla still damages every entity within twice the radius, and an {@code ItemEntity}
     * has 5 health against roughly 20 damage at that range - so before the rule shipped its own
     * damage calculator, mining with {@code blocks_explode} on destroyed the drop every time, and
     * {@code chaos.json} switches it on next to {@code random_drops}.
     */
    public static void blocksExplodeSparesTheBuildAndTheDrops(GameTestHelper helper) {
        // The block at `mined` has just been broken, so it is air and its drop is lying in it.
        BlockPos mined = new BlockPos(4, 1, 3);
        helper.setBlock(mined, Blocks.AIR);
        BlockPos build = new BlockPos(4, 1, 4);
        helper.setBlock(build, Blocks.OAK_PLANKS);
        BlockPos build2 = new BlockPos(3, 1, 3);
        helper.setBlock(build2, Blocks.OAK_PLANKS);

        helper.assertTrue(BlocksExplodeRule.RADIUS >= 2.0F,
                "the explosion has to be big enough to read on camera");
        helper.assertTrue(BlocksExplodeRule.COOLDOWN_TICKS > 0,
                "back-to-back breaks must be rate limited or a fast miner chain-explodes");

        ItemEntity drop = helper.spawnItem(Items.SADDLE, mined);
        BlocksExplodeRule.detonate(helper.getLevel(), helper.absolutePos(mined));

        helper.assertBlockPresent(Blocks.OAK_PLANKS, build);
        helper.assertBlockPresent(Blocks.OAK_PLANKS, build2);
        helper.assertTrue(!drop.isRemoved(),
                "the block's own drop must survive the explosion, or random_drops silently does nothing");
        helper.assertTrue(drop.getItem().is(Items.SADDLE), "and it must still be the same item");

        drop.discard();
        helper.setBlock(build, Blocks.AIR);
        helper.setBlock(build2, Blocks.AIR);
        helper.killAllEntities();
        helper.succeed();
    }

    /**
     * The per-player cooldown actually suppresses the second break, so a fast miner cannot chain
     * one explosion per block into a crater.
     */
    public static void blocksExplodeCooldownSuppressesTheSecondBreak(GameTestHelper helper) {
        BlocksExplodeRule rule = new BlocksExplodeRule();
        ServerLevel level = helper.getLevel();
        UUID miner = UUID.randomUUID();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));

        helper.assertTrue(rule.trigger(level, miner, pos),
                "the first break of a run should always explode");
        helper.assertFalse(rule.trigger(level, miner, pos),
                "a second break inside " + BlocksExplodeRule.COOLDOWN_TICKS + " ticks must be swallowed");
        helper.assertTrue(rule.trigger(level, UUID.randomUUID(), pos),
                "the cooldown is per player, not global");

        // Drop the queued blasts rather than letting them go off in another test's arena.
        TickScheduler.cancelAll(RuleIds.SCHED_EXPLODE);
        helper.succeed();
    }

    /**
     * {@code inventory_shuffle} permutes the 36 main slots: nothing is created, nothing is lost,
     * and armour and the off-hand are untouched.
     */
    public static void inventoryShufflePermutesTheMainSlots(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Item[] items = {Items.DIAMOND_SWORD, Items.APPLE, Items.TORCH, Items.OAK_LOG, Items.BREAD,
                Items.IRON_PICKAXE, Items.COBBLESTONE, Items.STRING, Items.BUCKET};
        for (int slot = 0; slot < items.length; slot++) {
            player.getInventory().setItem(slot, new ItemStack(items[slot], slot + 1));
        }
        player.getInventory().armor.set(0, new ItemStack(Items.IRON_HELMET));
        player.getInventory().offhand.set(0, new ItemStack(Items.SHIELD));

        Map<Item, Integer> before = mainSlotContents(player);
        RandomSource random = RandomSource.create(20260911L);

        boolean orderChanged = false;
        for (int attempt = 0; attempt < 3 && !orderChanged; attempt++) {
            InventoryShuffleRule.shuffle(player, random);
            for (int slot = 0; slot < items.length; slot++) {
                if (!player.getInventory().getItem(slot).is(items[slot])) {
                    orderChanged = true;
                    break;
                }
            }
        }

        helper.assertTrue(orderChanged,
                "three shuffles of nine items should have moved something");
        helper.assertValueEqual(mainSlotContents(player), before, "main inventory contents");
        helper.assertTrue(player.getInventory().armor.get(0).is(Items.IRON_HELMET),
                "the shuffle must never unequip armour");
        helper.assertTrue(player.getInventory().offhand.get(0).is(Items.SHIELD),
                "the shuffle must never empty the off-hand");
        helper.succeed();
    }

    private static Map<Item, Integer> mainSlotContents(Player player) {
        Map<Item, Integer> counts = new HashMap<>();
        for (int slot = 0; slot < InventoryShuffleRule.MAIN_SLOTS; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    /** The three shipped presets parse, and every rule they name is actually registered. */
    public static void shippedPresetsLoadAndNameKnownRules(GameTestHelper helper) {
        Map<String, RulePreset> presets = RulePresets.all(helper.getLevel().getServer());

        for (String expected : List.of("chaos", "speedrun_hard", "family_friendly")) {
            RulePreset preset = presets.get(expected);
            helper.assertTrue(preset != null, "preset '" + expected + "' should ship with the mod");
            helper.assertTrue(!preset.rules().isEmpty(), "preset '" + expected + "' should name rules");
            helper.assertTrue(preset.replace(),
                    "the shipped presets are 'replace' presets so one command sets up an episode");
            for (String id : preset.rules()) {
                helper.assertTrue(RuleRegistry.contains(id),
                        "preset '" + expected + "' names unknown rule '" + id + "'");
            }
        }
        helper.succeed();
    }

    /** The three data-pack tags load and actually contain what the rules assume they contain. */
    public static void ruleTagsAreLoadedFromTheDataPack(GameTestHelper helper) {
        helper.assertTrue(Blocks.BEDROCK.defaultBlockState().is(RuleTags.LAVA_FLOOR_IMMUNE),
                "bedrock should be lava-floor immune");
        helper.assertFalse(Blocks.STONE.defaultBlockState().is(RuleTags.LAVA_FLOOR_IMMUNE),
                "plain stone must still melt or the rule does nothing");

        helper.assertTrue(EntityType.WARDEN.is(RuleTags.NO_GIANT),
                "the warden should be left at its normal size");
        helper.assertFalse(EntityType.ZOMBIE.is(RuleTags.NO_GIANT),
                "the zombie is the demo mob and must be allowed to grow");

        helper.assertTrue(new ItemStack(Items.BEDROCK).is(RuleTags.NEVER_RANDOM),
                "bedrock should never be handed out as a random drop");
        helper.assertFalse(new ItemStack(Items.SADDLE).is(RuleTags.NEVER_RANDOM),
                "the saddle is the joke drop in the demo and must stay in the pool");

        helper.assertTrue(Blocks.EMERALD_BLOCK.defaultBlockState().is(RuleTags.SHOP_BLOCKS),
                "the emerald block is the shipped hearts_currency shop block");
        helper.assertFalse(Blocks.STONE.defaultBlockState().is(RuleTags.SHOP_BLOCKS),
                "every other block must stay a normal block or the world becomes one big shop");

        List<Item> pool = RandomItems.pool();
        helper.assertTrue(!pool.isEmpty(), "the roulette pool should not be empty");
        helper.assertFalse(pool.contains(Items.AIR), "air must never be handed out");
        helper.assertFalse(pool.contains(Items.BEDROCK),
                "#creator_rules:never_random must be honoured by the roulette pool too");
        helper.succeed();
    }

    /** {@code shop.json} names real items, fits the window and puts a price on every line. */
    public static void shopCatalogueResolvesRealItems(GameTestHelper helper) {
        List<ShopOffer> offers = ShopOffers.offers(helper.getLevel().getServer());

        helper.assertTrue(!offers.isEmpty(), "the shop should never be empty");
        helper.assertTrue(offers.size() <= ShopOffers.SLOTS,
                "the shop has to fit in a three-row chest, found " + offers.size() + " offers");
        for (ShopOffer offer : offers) {
            helper.assertTrue(offer.isValid(), offer.itemId() + " is priced wrong");
            helper.assertTrue(offer.item() != Items.AIR,
                    "shop.json names an item that does not exist: " + offer.itemId());
            ItemStack display = offer.displayStack();
            helper.assertTrue(display.getCount() == offer.count(), "the price tag should show the stack size");
            helper.assertTrue(display.get(DataComponents.LORE) != null,
                    offer.itemId() + " has no cost written on it");
        }
        helper.succeed();
    }

    /**
     * {@code random_drops} swaps a block's drops for its mapped item, always the same one, and
     * leaves a block that drops nothing dropping nothing.
     */
    public static void randomDropsReplacesDropsButNeverInventsThem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RuleContext context = new RuleContext(level.getServer());
        RandomDropsRule rule = new RandomDropsRule();
        rule.onEnable(context);

        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState stone = Blocks.STONE.defaultBlockState();

        List<ItemStack> first = rule.remapBlockDrops(context, level, pos, stone,
                List.of(new ItemStack(Items.COBBLESTONE)));
        helper.assertTrue(first != null && first.size() == 1,
                "stone should drop exactly one mapped item");
        helper.assertFalse(first.get(0).isEmpty(), "the mapped drop should not be an empty stack");
        helper.assertFalse(first.get(0).is(RuleTags.NEVER_RANDOM),
                "a blacklisted item must never be handed out, got " + first.get(0).getItem());

        List<ItemStack> second = rule.remapBlockDrops(context, level, pos, stone,
                List.of(new ItemStack(Items.COBBLESTONE)));
        helper.assertTrue(second.get(0).is(first.get(0).getItem()),
                "the same block must always drop the same item in the same world");

        helper.assertTrue(rule.remapBlockDrops(context, level, pos, stone, List.of()) == null,
                "a block that drops nothing must still drop nothing");

        rule.onDisable(context);
        helper.succeed();
    }

    /**
     * {@code /rule preset chaos} for real: the engine is live on a running server, a replace preset
     * switches off what it does not name, and {@code clearAll} puts everything back.
     *
     * <p>Deliberately uses only the two attribute rules, which touch online players and nothing
     * else - there are none in a GameTest world - and restores whatever was active before, because
     * every feature's tests share one server.
     */
    public static void presetApplyAndClearDriveTheEngine(GameTestHelper helper) {
        helper.assertTrue(RuleManager.isRunning(),
                "the engine must have started on the first server tick, or no /rule works at all");

        List<String> before = RuleManager.active();
        try {
            int active = RuleManager.applyPreset(
                    new RulePreset("gametest", List.of("one_heart", "gravity_x3"), true));
            helper.assertTrue(active == 2, "a replace preset naming two rules leaves two on, not " + active);
            helper.assertTrue(RuleManager.isActive("one_heart") && RuleManager.isActive("gravity_x3"),
                    "both named rules should be on");

            RuleManager.set("crafts_x10", true);
            RuleManager.applyPreset(new RulePreset("gametest2", List.of("one_heart"), true));
            helper.assertFalse(RuleManager.isActive("crafts_x10"),
                    "replace=true must switch off a rule the preset does not name");
            helper.assertFalse(RuleManager.isActive("gravity_x3"),
                    "including one the previous preset switched on");
            helper.assertTrue(RuleManager.isActive("one_heart"),
                    "a rule named by both presets must be left alone, not re-enabled");

            RuleManager.applyPreset(new RulePreset("gametest3", List.of("gravity_x3"), false));
            helper.assertTrue(RuleManager.isActive("one_heart"),
                    "replace=false must never switch anything off");
            helper.assertTrue(RuleManager.isActive("gravity_x3"), "and must add what it names");
        } finally {
            RuleManager.clearAll();
            for (String id : before) {
                RuleManager.set(id, true);
            }
        }
        helper.assertValueEqual(RuleManager.active(), before, "the active set after the test");
        helper.succeed();
    }

    private RulesGameTests() {
    }
}
