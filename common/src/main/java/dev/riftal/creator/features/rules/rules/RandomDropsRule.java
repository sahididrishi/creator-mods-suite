package dev.riftal.creator.features.rules.rules;

import dev.riftal.creator.features.rules.RuleTags;
import dev.riftal.creator.features.rules.api.Rule;
import dev.riftal.creator.features.rules.api.RuleContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static dev.riftal.creator.Constants.LOG;

/**
 * <b>Minecraft but every block and mob drops the wrong thing.</b>
 *
 * <p>Stone always drops a saddle; the next stone also drops a saddle. The mapping is a seeded
 * permutation of the item registry (see {@link RandomDropsMapping}), so it is stable for a world
 * and different in the next one, and it survives {@code /reload} and restarts without being stored
 * anywhere.
 *
 * <p>Vanilla drops are replaced at the point they are produced rather than by rewriting loot
 * tables, so switching the rule on takes effect on the very next block - no resource reload, no
 * one-second stall on camera.
 *
 * <p>A block that normally drops nothing still drops nothing, and items in
 * {@code #creator_rules:never_random} are never handed out - the mapping is rebuilt whenever the
 * data packs are reloaded, so editing that tag takes effect without a restart.
 */
public final class RandomDropsRule implements Rule {

    private RandomDropsMapping mapping;
    private long mappingSeed = Long.MIN_VALUE;
    private ResourceManager mappingManager;

    @Override
    public String id() {
        return "random_drops";
    }

    @Override
    public void onEnable(RuleContext ctx) {
        mapping = null;
        mappingFor(ctx);
    }

    @Override
    public void onDisable(RuleContext ctx) {
        invalidate();
    }

    /**
     * Forces the mapping to be rebuilt. {@code /rule reload} calls this because the target list is
     * filtered by {@code #creator_rules:never_random}, which a data pack can change under us.
     */
    public void invalidate() {
        mapping = null;
        mappingSeed = Long.MIN_VALUE;
        mappingManager = null;
    }

    /** Builds (once per world) and returns the mapping. Public so the GameTest can assert on it. */
    public RandomDropsMapping mappingFor(RuleContext ctx) {
        long seed = ctx.worldSeed();
        // The manager instance is vanilla's own "the data packs have been reloaded" signal, so a
        // plain /reload rebuilds the mapping against the new #never_random contents for free.
        ResourceManager manager = ctx.server().getResourceManager();
        if (mapping != null && seed == mappingSeed && manager == mappingManager) {
            return mapping;
        }
        List<String> sources = new ArrayList<>();
        for (ResourceLocation blockId : BuiltInRegistries.BLOCK.keySet()) {
            sources.add(RandomDropsMapping.blockKey(blockId.toString()));
        }
        for (ResourceLocation entityId : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            sources.add(RandomDropsMapping.entityKey(entityId.toString()));
        }

        List<String> targets = new ArrayList<>();
        for (Holder.Reference<Item> holder : BuiltInRegistries.ITEM.holders().toList()) {
            if (holder.value() == Items.AIR || holder.is(RuleTags.NEVER_RANDOM)) {
                continue;
            }
            targets.add(holder.key().location().toString());
        }

        mapping = RandomDropsMapping.build(seed, sources, targets);
        mappingSeed = seed;
        mappingManager = manager;
        LOG.info("[rules] random_drops mapped {} source(s) onto {} item(s)",
                mapping.size(), targets.size());
        return mapping;
    }

    @Override
    public List<ItemStack> remapBlockDrops(RuleContext ctx, ServerLevel level, BlockPos pos,
                                           BlockState state, List<ItemStack> original) {
        if (original.isEmpty()) {
            return null;
        }
        ItemStack replacement = stackFor(ctx,
                RandomDropsMapping.blockKey(idOf(state.getBlock())));
        if (replacement.isEmpty()) {
            return null;
        }
        // A fresh mutable list: vanilla hands its drop list to other code that may add to it.
        List<ItemStack> drops = new ArrayList<>(1);
        drops.add(replacement);
        return drops;
    }

    @Override
    public List<ItemStack> remapMobDrops(RuleContext ctx, LivingEntity entity) {
        if (entity instanceof Player) {
            return null;
        }
        ItemStack replacement = stackFor(ctx,
                RandomDropsMapping.entityKey(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()));
        if (replacement.isEmpty()) {
            return null;
        }
        List<ItemStack> drops = new ArrayList<>(1);
        drops.add(replacement);
        return drops;
    }

    private ItemStack stackFor(RuleContext ctx, String sourceKey) {
        String target = mappingFor(ctx).targetFor(sourceKey);
        if (target == null) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(target);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static String idOf(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }
}
