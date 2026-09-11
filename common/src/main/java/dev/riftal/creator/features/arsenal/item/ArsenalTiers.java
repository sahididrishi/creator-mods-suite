package dev.riftal.creator.features.arsenal.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * Material tiers for the Arsenal weapons.
 *
 * <p>1.21.1's {@code Tier} has {@code getIncorrectBlocksForDrops()} returning a
 * {@code TagKey<Block>} - there is no {@code getLevel()} any more. The repair ingredient is built
 * lazily because {@code Ingredient.of(...)} touches item registries, which are not populated while
 * this enum's constants are constructed.
 */
public enum ArsenalTiers implements Tier {

    /** Grapple Blade: iron-grade, quick, modest bonus damage. */
    GRAPPLE(BlockTags.INCORRECT_FOR_IRON_TOOL, 1200, 8.0F, 3.0F, 14, () -> Ingredient.of(Items.IRON_INGOT)),

    /** Gravity Hammer: heavy, slow, hits like a falling anvil. */
    GRAVITY(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1800, 6.0F, 8.0F, 10, () -> Ingredient.of(Items.NETHERITE_SCRAP)),

    /** Soul Scythe: sharp, highly enchantable, repaired with soul sand. */
    SOUL(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1500, 9.0F, 5.0F, 22, () -> Ingredient.of(Items.SOUL_SAND));

    private final TagKey<Block> incorrectBlocksForDrops;
    private final int uses;
    private final float speed;
    private final float attackDamageBonus;
    private final int enchantmentValue;
    private final Supplier<Ingredient> repairIngredientFactory;

    private Ingredient repairIngredient;

    ArsenalTiers(TagKey<Block> incorrectBlocksForDrops, int uses, float speed, float attackDamageBonus,
                 int enchantmentValue, Supplier<Ingredient> repairIngredientFactory) {
        this.incorrectBlocksForDrops = incorrectBlocksForDrops;
        this.uses = uses;
        this.speed = speed;
        this.attackDamageBonus = attackDamageBonus;
        this.enchantmentValue = enchantmentValue;
        this.repairIngredientFactory = repairIngredientFactory;
    }

    @Override
    public int getUses() {
        return this.uses;
    }

    @Override
    public float getSpeed() {
        return this.speed;
    }

    @Override
    public float getAttackDamageBonus() {
        return this.attackDamageBonus;
    }

    @Override
    public TagKey<Block> getIncorrectBlocksForDrops() {
        return this.incorrectBlocksForDrops;
    }

    @Override
    public int getEnchantmentValue() {
        return this.enchantmentValue;
    }

    @Override
    public Ingredient getRepairIngredient() {
        Ingredient cached = this.repairIngredient;
        if (cached == null) {
            cached = this.repairIngredientFactory.get();
            this.repairIngredient = cached;
        }
        return cached;
    }
}
