package dev.riftal.creator.features.arsenal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.riftal.creator.features.arsenal.item.GrappleBladeItem;
import dev.riftal.creator.features.arsenal.item.GravityHammerItem;
import dev.riftal.creator.features.arsenal.item.SoulScytheItem;
import dev.riftal.creator.features.arsenal.item.StormBowItem;
import dev.riftal.creator.features.arsenal.item.WeaponTooltips;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The tooltip is the last shot of the demo clip, so both lines have to be translated (never a raw
 * key) and coloured. Components are plain data - no registry is touched building one.
 */
class WeaponTooltipsTest {

    private static String keyOf(Component component) {
        assertNotNull(component.getContents());
        if (component.getContents() instanceof TranslatableContents translatable) {
            return translatable.getKey();
        }
        throw new AssertionError("tooltip line is not translatable: " + component.getContents());
    }

    @Test
    void keysAreNamespacedPerItem() {
        assertEquals("item.creator_arsenal.soul_scythe.desc",
                WeaponTooltips.key(SoulScytheItem.PATH, "desc"));
        assertEquals("item.creator_arsenal.grapple_blade.flavour",
                WeaponTooltips.key(GrappleBladeItem.PATH, "flavour"));
    }

    @Test
    void aWeaponGetsAFlavourLineThenAMechanicLine() {
        List<Component> lines = new ArrayList<>();

        WeaponTooltips.weapon(lines, SoulScytheItem.PATH, ChatFormatting.DARK_AQUA);

        assertEquals(2, lines.size());
        assertEquals("item.creator_arsenal.soul_scythe.flavour", keyOf(lines.get(0)));
        assertEquals("item.creator_arsenal.soul_scythe.desc", keyOf(lines.get(1)));
    }

    @Test
    void theFlavourLineCarriesTheWeaponsColourAndTheMechanicLineIsGrey() {
        List<Component> lines = new ArrayList<>();

        WeaponTooltips.weapon(lines, StormBowItem.PATH, ChatFormatting.LIGHT_PURPLE);

        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE),
                lines.get(0).getStyle().getColor());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY),
                lines.get(1).getStyle().getColor());
    }

    @Test
    void everyWeaponProducesTwoDistinctKeys() {
        List<String> keys = new ArrayList<>();
        for (String path : List.of(GrappleBladeItem.PATH, StormBowItem.PATH,
                GravityHammerItem.PATH, SoulScytheItem.PATH)) {
            List<Component> lines = new ArrayList<>();
            WeaponTooltips.weapon(lines, path, ChatFormatting.AQUA);
            keys.add(keyOf(lines.get(0)));
            keys.add(keyOf(lines.get(1)));
        }

        assertEquals(8, keys.size());
        assertEquals(8, keys.stream().distinct().count(), "tooltip keys collided: " + keys);
    }
}
