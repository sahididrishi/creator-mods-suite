package dev.riftal.creator.features.vault.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The key the Cursed Altar eats. Glints without an enchantment so it reads as "special" on camera
 * without polluting the stack with enchantment data components.
 *
 * <p>The whole interaction lives in {@code CursedAltarBlock#useItemOn}: the key itself does
 * nothing when used on anything else, which keeps "right-click the altar" the only story the clip
 * has to tell.
 */
public class VaultKeyItem extends Item {

    public VaultKeyItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable("item.creator_vault.vault_key.tooltip")
                .withStyle(ChatFormatting.DARK_PURPLE));
    }
}
