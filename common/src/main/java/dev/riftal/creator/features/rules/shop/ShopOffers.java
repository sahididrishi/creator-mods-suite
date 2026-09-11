package dev.riftal.creator.features.rules.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.riftal.creator.features.rules.RulesFeature;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.riftal.creator.Constants.LOG;

/**
 * The hearts-shop catalogue, read from {@code data/creator_rules/shop.json} so a pack maker can
 * re-price an episode without touching code, with a built-in list as the fallback.
 *
 * <p>Also the home of the affordability rule, which is pure arithmetic and unit-tested: a purchase
 * may never leave a player under one heart.
 */
public final class ShopOffers {

    /** Maximum health removed per heart of price. */
    public static final double HEALTH_PER_HEART = 2.0D;

    /** A player may never be taken below this much maximum health. */
    public static final double MINIMUM_HEALTH = 2.0D;

    /** Slots in the three-row chest GUI. */
    public static final int SLOTS = 27;

    private static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath(RulesFeature.NAMESPACE, "shop.json");

    private static ResourceManager cachedFor;
    private static List<ShopOffer> cache;

    /**
     * True when a player with {@code maxHealth} can pay {@code hearts} and still have at least one
     * heart left.
     */
    public static boolean canAfford(double maxHealth, int hearts) {
        if (hearts <= 0) {
            return false;
        }
        return maxHealth - hearts * HEALTH_PER_HEART >= MINIMUM_HEALTH;
    }

    /** The offers for this server, at most {@link #SLOTS} of them. */
    public static List<ShopOffer> offers(MinecraftServer server) {
        if (server == null) {
            return defaults();
        }
        ResourceManager manager = server.getResourceManager();
        if (manager != cachedFor || cache == null) {
            cache = read(manager);
            cachedFor = manager;
        }
        return cache;
    }

    /** Drops the cached catalogue. Called on server start and by {@code /rule reload}. */
    public static void clear() {
        cachedFor = null;
        cache = null;
    }

    private static List<ShopOffer> read(ResourceManager manager) {
        Optional<Resource> resource = manager.getResource(FILE);
        if (resource.isEmpty()) {
            return defaults();
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            JsonObject json = GsonHelper.parse(reader);
            JsonArray array = GsonHelper.getAsJsonArray(json, "offers");
            List<ShopOffer> parsed = new ArrayList<>();
            for (int i = 0; i < array.size() && parsed.size() < SLOTS; i++) {
                ShopOffer offer = ShopOffer.fromJson(GsonHelper.convertToJsonObject(array.get(i), "offers[" + i + "]"));
                if (offer.isValid()) {
                    parsed.add(offer);
                } else {
                    LOG.warn("[rules] shop offer {} has a non-positive price or a silly count; skipped", i);
                }
            }
            return parsed.isEmpty() ? defaults() : List.copyOf(parsed);
        } catch (Exception e) {
            LOG.warn("[rules] could not read {}: {}; falling back to the built-in shop", FILE, e.toString());
            return defaults();
        }
    }

    /** The shipped catalogue, used when no data pack provides one. */
    public static List<ShopOffer> defaults() {
        List<ShopOffer> list = new ArrayList<>();
        list.add(offer("minecraft:netherite_sword", 1, 3));
        list.add(offer("minecraft:diamond_pickaxe", 1, 2));
        list.add(offer("minecraft:elytra", 1, 4));
        list.add(offer("minecraft:totem_of_undying", 1, 3));
        list.add(offer("minecraft:enchanted_golden_apple", 2, 2));
        list.add(offer("minecraft:diamond", 8, 1));
        list.add(offer("minecraft:ancient_debris", 4, 2));
        list.add(offer("minecraft:golden_carrot", 16, 1));
        list.add(offer("minecraft:ender_pearl", 8, 1));
        list.add(offer("minecraft:obsidian", 16, 1));
        list.add(offer("minecraft:tnt", 16, 1));
        list.add(offer("minecraft:beacon", 1, 5));
        return List.copyOf(list);
    }

    private static ShopOffer offer(String item, int count, int hearts) {
        return new ShopOffer(ResourceLocation.parse(item), count, hearts);
    }

    private ShopOffers() {
    }
}
