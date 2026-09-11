package dev.riftal.creator.features.arsenal.item;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.arsenal.ArsenalFeature;
import dev.riftal.creator.features.arsenal.entity.StormArrowEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Bow whose arrows call lightning - but only at full draw.
 *
 * <p>1.21.1 routes every bow shot through
 * {@code ProjectileWeaponItem#createProjectile(Level, LivingEntity, ItemStack, ItemStack, boolean)},
 * and vanilla passes {@code isCrit = (getPowerForTime(charge) == 1.0F)} - i.e. exactly full draw.
 * That single flag is the "charged" state, so half-drawn shots stay ordinary arrows with no
 * additional code path.
 */
public class StormBowItem extends BowItem {

    /** Registry path, also the lang-key suffix. */
    public static final String PATH = "storm_bow";

    /** Durability of the bow. */
    public static final int DURABILITY = 600;

    public StormBowItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon,
                                          ItemStack ammo, boolean isCrit) {
        if (!CreatorMods.isEnabled(ArsenalFeature.ID) || weapon.isEmpty()) {
            return super.createProjectile(level, shooter, weapon, ammo, isCrit);
        }
        StormArrowEntity arrow = new StormArrowEntity(level, shooter, ammo, weapon);
        arrow.setCritArrow(isCrit);
        arrow.setCharged(isCrit);
        return arrow;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        WeaponTooltips.weapon(tooltipComponents, PATH, ChatFormatting.LIGHT_PURPLE);
    }
}
