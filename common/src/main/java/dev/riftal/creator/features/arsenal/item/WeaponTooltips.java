package dev.riftal.creator.features.arsenal.item;

import dev.riftal.creator.features.arsenal.ArsenalFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Shared tooltip lines for the four signature weapons.
 *
 * <p>Every weapon gets a flavour line in its rarity colour and a mechanic line in grey, both
 * translated - the keys live in {@code assets/creator_arsenal/lang/en_us.json}. Rarity itself comes
 * from {@code DataComponents.RARITY} via {@code Item.Properties#rarity}, so the item name is already
 * coloured by vanilla; nothing here renames the stack.
 */
public final class WeaponTooltips {

    /** Adds {@code item.creator_arsenal.<path>.flavour} in {@code colour}. */
    public static void flavour(List<Component> lines, String path, ChatFormatting colour) {
        lines.add(Component.translatable(key(path, "flavour")).withStyle(colour));
    }

    /** Adds {@code item.creator_arsenal.<path>.desc} in grey - what the weapon actually does. */
    public static void mechanic(List<Component> lines, String path) {
        lines.add(Component.translatable(key(path, "desc")).withStyle(ChatFormatting.GRAY));
    }

    /** Adds both lines, flavour first. The house style for every Arsenal weapon. */
    public static void weapon(List<Component> lines, String path, ChatFormatting colour) {
        flavour(lines, path, colour);
        mechanic(lines, path);
    }

    /** {@code item.creator_arsenal.<path>.<suffix>} */
    public static String key(String path, String suffix) {
        return "item." + ArsenalFeature.NAMESPACE + "." + path + "." + suffix;
    }

    private WeaponTooltips() {
    }
}
