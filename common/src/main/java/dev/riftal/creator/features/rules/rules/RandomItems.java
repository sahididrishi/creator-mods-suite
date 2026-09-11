package dev.riftal.creator.features.rules.rules;

import dev.riftal.creator.features.rules.RuleTags;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The pool {@code item_roulette} hands out of: every registered item except air and anything in
 * {@code #creator_rules:never_random}.
 *
 * <p>Cached because the item registry is frozen for the session; the cache is dropped whenever a
 * server starts, because the tag contents come from data packs and may have changed.
 */
public final class RandomItems {

    private static List<Item> cache;

    /** Drops the cached pool. Called when a server starts or data packs reload. */
    public static void invalidate() {
        cache = null;
    }

    /** Every item that may be handed out, in registry order. Never empty in a real game. */
    public static List<Item> pool() {
        List<Item> pool = cache;
        if (pool == null) {
            List<Item> built = new ArrayList<>();
            for (Holder.Reference<Item> holder : BuiltInRegistries.ITEM.holders().toList()) {
                if (holder.value() != Items.AIR && !holder.is(RuleTags.NEVER_RANDOM)) {
                    built.add(holder.value());
                }
            }
            pool = List.copyOf(built);
            cache = pool;
        }
        return pool;
    }

    /** A uniformly chosen item, or {@link Items#AIR} if the pool is somehow empty. */
    public static Item pick(RandomSource random) {
        List<Item> pool = pool();
        return pool.isEmpty() ? Items.AIR : pool.get(random.nextInt(pool.size()));
    }

    private RandomItems() {
    }
}
