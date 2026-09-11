package dev.riftal.creator.features.arsenal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.arsenal.item.ArsenalTiers;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import org.junit.jupiter.api.Test;

/**
 * The three weapon tiers, against plan 07 section 5's table. These are the numbers the tooltip
 * shows on camera, so a silent change to one of them is a change to the recording.
 *
 * <p>{@code getRepairIngredient()} is deliberately not touched here: {@code Ingredient.of(Items.X)}
 * reads the item registry, which is empty in a unit test. The GameTest covers it live.
 */
class ArsenalTiersTest {

    @Test
    void grappleIsIronGrade() {
        Tier tier = ArsenalTiers.GRAPPLE;

        assertEquals(1200, tier.getUses());
        assertEquals(8.0F, tier.getSpeed(), 1.0E-4F);
        assertEquals(3.0F, tier.getAttackDamageBonus(), 1.0E-4F);
        assertEquals(14, tier.getEnchantmentValue());
        assertSame(BlockTags.INCORRECT_FOR_IRON_TOOL, tier.getIncorrectBlocksForDrops());
    }

    @Test
    void gravityIsTheHeavyDiamondGradeTier() {
        Tier tier = ArsenalTiers.GRAVITY;

        assertEquals(1800, tier.getUses());
        assertEquals(6.0F, tier.getSpeed(), 1.0E-4F);
        assertEquals(8.0F, tier.getAttackDamageBonus(), 1.0E-4F);
        assertEquals(10, tier.getEnchantmentValue());
        assertSame(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, tier.getIncorrectBlocksForDrops());
    }

    @Test
    void soulIsTheMostEnchantableOfTheThree() {
        Tier tier = ArsenalTiers.SOUL;

        assertEquals(1500, tier.getUses());
        assertEquals(9.0F, tier.getSpeed(), 1.0E-4F);
        assertEquals(5.0F, tier.getAttackDamageBonus(), 1.0E-4F);
        assertEquals(22, tier.getEnchantmentValue());
        assertSame(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, tier.getIncorrectBlocksForDrops());

        for (ArsenalTiers other : ArsenalTiers.values()) {
            assertTrue(tier.getEnchantmentValue() >= other.getEnchantmentValue(),
                    "SOUL should be the most enchantable tier, but " + other + " is higher");
        }
    }

    /**
     * The displayed attack damage is {@code 1 (player base) + createAttributes' damage + tier
     * bonus}. Plan 07 wants 7 / 12 / 9 on the blade, hammer and scythe; the {@code +3} in each
     * constructor is the {@code attackDamage} argument passed in {@code ArsenalFeature}.
     */
    @Test
    void theTooltipDamageNumbersComeOutAsThePlanPromises() {
        assertEquals(7.0F, 1.0F + 3.0F + ArsenalTiers.GRAPPLE.getAttackDamageBonus(), 1.0E-4F);
        assertEquals(12.0F, 1.0F + 3.0F + ArsenalTiers.GRAVITY.getAttackDamageBonus(), 1.0E-4F);
        assertEquals(9.0F, 1.0F + 3.0F + ArsenalTiers.SOUL.getAttackDamageBonus(), 1.0E-4F);
    }

    @Test
    void everyTierIsUsableAndTagged() {
        for (ArsenalTiers tier : ArsenalTiers.values()) {
            assertTrue(tier.getUses() > 0, tier + " must have durability");
            assertTrue(tier.getSpeed() > 0.0F, tier + " must have a mining speed");
            assertNotNull(tier.getIncorrectBlocksForDrops(), tier + " must declare an incorrect-blocks tag");
        }
        assertEquals(3, ArsenalTiers.values().length);
    }
}
