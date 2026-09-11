package dev.riftal.creator.features.rules.rules;

import dev.riftal.creator.features.rules.RuleTags;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The pool {@code item_roulette} hands out of: every registered item except air and anything in
 * {@code #creator_rules:never_random}.
 *
 * <p>The item registry is frozen for the session but the tag is not - it comes from a data pack and
 * changes on every {@code /reload}. The pool is therefore cached against the {@link ResourceManager}
 * instance, which vanilla replaces on each reload, exactly as {@code ShopOffers} and
 * {@code RulePresets} already do. Passing no server keeps whatever is cached, which is what the
 * pure-logic tests want.
 */
public final class RandomItems {

    private static ResourceManager cachedFor;
    private static List<Item> cache;

    /** Drops the cached pool. Called when a server starts, stops or {@code /rule reload} runs. */
    public static void invalidate() {
        cachedFor = null;
        cache = null;
    }

    /** Every item that may be handed out, in registry order. Never empty in a real game. */
    public static List<Item> pool() {
        return pool(null);
    }

    /**
     * Every item that may be handed out, rebuilt when this server's data packs have been reloaded
     * since the last call.
     */
    public static List<Item> pool(MinecraftServer server) {
        ResourceManager manager = server == null ? null : server.getResourceManager();
        if (cache == null || (manager != null && manager != cachedFor)) {
            cache = build();
            cachedFor = manager;
        }
        return cache;
    }

    private static List<Item> build() {
        List<Item> built = new ArrayList<>();
        for (Holder.Reference<Item> holder : BuiltInRegistries.ITEM.holders().toList()) {
            if (holder.value() != Items.AIR && !holder.is(RuleTags.NEVER_RANDOM)) {
                built.add(holder.value());
            }
        }
        return List.copyOf(built);
    }

    /** A uniformly chosen item, or {@link Items#AIR} if the pool is somehow empty. */
    public static Item pick(RandomSource random) {
        return pick(null, random);
    }

    /** A uniformly chosen item from this server's (possibly just reloaded) pool. */
    public static Item pick(MinecraftServer server, RandomSource random) {
        List<Item> pool = pool(server);
        return pool.isEmpty() ? Items.AIR : pool.get(random.nextInt(pool.size()));
    }

    private RandomItems() {
    }
}
