package dev.riftal.creator.features.rules.rules;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.rules.RuleIds;
import dev.riftal.creator.features.rules.RuleManager;
import dev.riftal.creator.features.rules.RulesFeature;
import dev.riftal.creator.features.rules.api.Rule;
import dev.riftal.creator.features.rules.api.RuleContext;
import dev.riftal.creator.features.rules.shop.ShopMenu;
import dev.riftal.creator.features.rules.shop.ShopOffer;
import dev.riftal.creator.features.rules.shop.ShopOffers;
import dev.riftal.creator.features.rules.util.RuleAttributes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;

/**
 * <b>Minecraft but hearts are money.</b>
 *
 * <p>{@code /shop} opens a chest of gear priced in hearts; buying costs permanent maximum health
 * (two points per heart) and the bar visibly shrinks. A purchase that would leave the player under
 * one heart is refused, which is also what keeps this rule composable with {@code one_heart}.
 *
 * <p>How much has been spent lives in a per-player attachment that survives death, so the debt is
 * not a respawn away from being cleared. The max-health modifier itself is transient and rebuilt
 * from that number on every join, enable and respawn - the attachment is the single source of
 * truth, so the two can never drift apart.
 */
public final class HeartsCurrencyRule implements Rule {

    /** The rule id, used by the shop to refuse purchases while the rule is off. */
    public static final String ID = "hearts_currency";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void onEnable(RuleContext ctx) {
        for (ServerPlayer player : ctx.players()) {
            applyTo(player);
        }
    }

    @Override
    public void onDisable(RuleContext ctx) {
        // The debt is kept (and re-applied if the rule comes back on); only the modifier goes.
        for (ServerPlayer player : ctx.players()) {
            stripFrom(player);
            RuleAttributes.clampHealth(player);
        }
    }

    @Override
    public void onPlayerJoin(RuleContext ctx, ServerPlayer player) {
        applyTo(player);
    }

    @Override
    public void onPlayerRespawn(RuleContext ctx, ServerPlayer player) {
        applyTo(player);
    }

    @Override
    public void stripFrom(ServerPlayer player) {
        RuleAttributes.remove(player, Attributes.MAX_HEALTH, RuleIds.HEARTS_SPENT);
    }

    /** Rebuilds the max-health penalty from the stored debt. */
    public static void applyTo(ServerPlayer player) {
        int spent = spent(player);
        if (spent <= 0) {
            RuleAttributes.remove(player, Attributes.MAX_HEALTH, RuleIds.HEARTS_SPENT);
            return;
        }
        RuleAttributes.apply(player, Attributes.MAX_HEALTH, RuleIds.HEARTS_SPENT,
                -ShopOffers.HEALTH_PER_HEART * spent, AttributeModifier.Operation.ADD_VALUE);
        RuleAttributes.clampHealth(player);
    }

    /** Hearts this player has spent so far. */
    public static int spent(ServerPlayer player) {
        return RulesFeature.heartsSpent() == null ? 0 : RulesFeature.heartsSpent().get(player);
    }

    /** Opens the shop for one player. Refused - with a reason - while the rule is off. */
    public static void openShop(ServerPlayer player) {
        if (!RuleManager.isActive(ID)) {
            player.displayClientMessage(
                    Component.translatable("commands.creator_rules.shop.inactive")
                            .withStyle(ChatFormatting.RED), true);
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, owner) -> new ShopMenu(containerId, inventory,
                        ShopOffers.offers(player.getServer())),
                Component.translatable("container.creator_rules.shop")));
    }

    /**
     * Buys one offer. Every check is repeated here because this is reached from a menu click, i.e.
     * from a packet the client controls.
     *
     * @return true when the player actually paid and received the goods
     */
    public static boolean purchase(ServerPlayer player, ShopOffer offer) {
        if (!RuleManager.isActive(ID)) {
            return false;
        }
        if (offer == null || !offer.isValid() || offer.item() == Items.AIR) {
            return false;
        }
        if (!ShopOffers.canAfford(player.getMaxHealth(), offer.hearts())) {
            player.displayClientMessage(
                    Component.translatable("container.creator_rules.shop.too_poor")
                            .withStyle(ChatFormatting.RED), true);
            Fx.sound(player.serverLevel(), player.position(), SoundEvents.ITEM_BREAK,
                    SoundSource.PLAYERS, 0.6F, 0.8F);
            return false;
        }
        if (RulesFeature.heartsSpent() == null) {
            return false;
        }
        RulesFeature.heartsSpent().update(player, current -> current + offer.hearts());
        applyTo(player);
        player.getInventory().placeItemBackInInventory(offer.purchaseStack());
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.ANVIL_USE,
                SoundSource.PLAYERS, 0.7F, 1.2F);
        player.displayClientMessage(
                Component.translatable("container.creator_rules.shop.bought",
                                offer.purchaseStack().getHoverName(), offer.hearts())
                        .withStyle(ChatFormatting.GREEN), true);
        return true;
    }
}
