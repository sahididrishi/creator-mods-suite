package dev.riftal.creator.features.rules.shop;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * One line of the hearts shop: an item and what it costs in hearts.
 *
 * @param itemId what the player gets
 * @param count  how many
 * @param hearts price in hearts; each heart is two points of maximum health
 */
public record ShopOffer(ResourceLocation itemId, int count, int hearts) {

    /**
     * Parses one entry of {@code data/creator_rules/shop.json}. Pure - no registries - so it can be
     * validated in a unit test.
     *
     * @throws com.google.gson.JsonParseException if the entry is malformed
     */
    public static ShopOffer fromJson(JsonObject json) {
        String item = GsonHelper.getAsString(json, "item");
        ResourceLocation id = ResourceLocation.parse(item);
        int count = GsonHelper.getAsInt(json, "count", 1);
        int hearts = GsonHelper.getAsInt(json, "hearts");
        return new ShopOffer(id, count, hearts);
    }

    /** True when this offer is worth showing: a real price and a sane stack size. */
    public boolean isValid() {
        return hearts > 0 && count >= 1 && count <= 64;
    }

    /** The item, or {@link Items#AIR} when the id is not registered (a stale data pack). */
    public Item item() {
        return BuiltInRegistries.ITEM.get(itemId);
    }

    /** The stack shown in the chest GUI, with its price written into the lore. */
    public ItemStack displayStack() {
        ItemStack stack = new ItemStack(item(), count);
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("container.creator_rules.shop.cost", hearts)
                        .withStyle(ChatFormatting.RED))));
        return stack;
    }

    /** The stack actually handed over on purchase. */
    public ItemStack purchaseStack() {
        return new ItemStack(item(), count);
    }
}
