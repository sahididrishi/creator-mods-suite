package dev.riftal.creator.features.rules;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * The three data-pack tags the rules consult. All three ship with sensible defaults under
 * {@code data/creator_rules/tags/} and are meant to be edited by pack makers rather than by code.
 */
public final class RuleTags {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(RulesFeature.NAMESPACE, path);
    }

    /** Items {@code random_drops} and {@code item_roulette} will never hand out. */
    public static final TagKey<Item> NEVER_RANDOM = TagKey.create(Registries.ITEM, id("never_random"));

    /** Blocks {@code lava_floor} refuses to melt. */
    public static final TagKey<Block> LAVA_FLOOR_IMMUNE =
            TagKey.create(Registries.BLOCK, id("lava_floor_immune"));

    /** Mobs {@code giant_mobs} leaves at their normal size. */
    public static final TagKey<EntityType<?>> NO_GIANT =
            TagKey.create(Registries.ENTITY_TYPE, id("no_giant"));

    private RuleTags() {
    }
}
