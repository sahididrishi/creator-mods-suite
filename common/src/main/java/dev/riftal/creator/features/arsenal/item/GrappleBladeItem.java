package dev.riftal.creator.features.arsenal.item;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.arsenal.ArsenalFeature;
import dev.riftal.creator.features.arsenal.entity.GrappleHookEntity;
import dev.riftal.creator.features.arsenal.mechanic.GrappleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Sword that fires a hook on right-click and reels the wielder to whatever it bites.
 *
 * <p>Left-click is an ordinary sword swing; the whole trick lives in {@link #use}. One hook per
 * player: using the blade again while a hook is live retracts it instead of spawning a second, and
 * that path deliberately ignores the cooldown so the creator can always cut the line on camera.
 *
 * <p>All decisions are server-side. The client returns SUCCESS so the arm swings, and never reads
 * {@link GrappleManager}, whose maps belong to the logical server.
 */
public class GrappleBladeItem extends SwordItem {

    /** Registry path, also the lang-key suffix. */
    public static final String PATH = "grapple_blade";

    /** Cooldown after firing, in ticks. */
    public static final int COOLDOWN_TICKS = 20;

    /** Launch speed of the hook, in blocks per tick. */
    public static final float HOOK_VELOCITY = 1.6F;

    public GrappleBladeItem(Tier tier, Item.Properties properties) {
        super(tier, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!CreatorMods.isEnabled(ArsenalFeature.ID)) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        GrappleHookEntity live = GrappleManager.hookOf(player);
        if (live != null) {
            live.retract();
            return InteractionResultHolder.consume(stack);
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }

        GrappleHookEntity hook = new GrappleHookEntity(level, player);
        hook.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, HOOK_VELOCITY, 0.5F);
        level.addFreshEntity(hook);
        GrappleManager.setHook(player, hook);
        Fx.sound(level, player.position(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.8F, 0.7F);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        WeaponTooltips.weapon(tooltipComponents, PATH, ChatFormatting.AQUA);
    }
}
