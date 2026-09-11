package dev.riftal.creator.features.arsenal.item;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.arsenal.ArsenalFeature;
import dev.riftal.creator.features.arsenal.mechanic.GravitySlam;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Slow, brutal hammer. Right-click lifts everything living within six blocks, hangs it for a second
 * and a half, then slams it into the ground.
 *
 * <p>It extends {@code SwordItem} so it still crits and sweeps like a melee weapon; only the stats
 * and the right-click are different. The eight second cooldown is vanilla {@code ItemCooldowns}, so
 * the icon plays the wipe animation for free.
 */
public class GravityHammerItem extends SwordItem {

    /** Registry path, also the lang-key suffix. */
    public static final String PATH = "gravity_hammer";

    public GravityHammerItem(Tier tier, Item.Properties properties) {
        super(tier, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!CreatorMods.isEnabled(ArsenalFeature.ID) || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        GravitySlam.start(serverLevel, serverPlayer);
        player.getCooldowns().addCooldown(this, GravitySlam.COOLDOWN_TICKS);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        WeaponTooltips.weapon(tooltipComponents, PATH, ChatFormatting.LIGHT_PURPLE);
    }
}
