package dev.riftal.creator.features.rules.shop;

import dev.riftal.creator.features.rules.rules.HeartsCurrencyRule;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The {@code /shop} window: a plain three-row chest on the client (so no screen class, no menu type
 * and no client code at all) whose slots are read-only price tags on the server.
 *
 * <p>Every interaction with the top 27 slots is intercepted in {@link #clicked}: the display stack
 * never moves, and a left click is a purchase. Shift-clicking is disabled outright by returning an
 * empty stack from {@link #quickMoveStack}, which is the simplest airtight way to stop players
 * posting their own items into a container that is not backed by anything.
 */
public final class ShopMenu extends ChestMenu {

    private final List<ShopOffer> offers;

    public ShopMenu(int containerId, Inventory playerInventory, List<ShopOffer> offers) {
        super(MenuType.GENERIC_9x3, containerId, playerInventory, display(offers), 3);
        this.offers = List.copyOf(offers);
    }

    private static Container display(List<ShopOffer> offers) {
        SimpleContainer container = new SimpleContainer(ShopOffers.SLOTS);
        for (int slot = 0; slot < ShopOffers.SLOTS && slot < offers.size(); slot++) {
            container.setItem(slot, offers.get(slot).displayStack());
        }
        return container;
    }

    /** Shift-click is disabled in the shop: nothing may be moved in or out by hand. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < ShopOffers.SLOTS) {
            if (player instanceof ServerPlayer serverPlayer && slotId < offers.size()) {
                HeartsCurrencyRule.purchase(serverPlayer, offers.get(slotId));
            }
            // Re-send the whole window so the client's optimistic move is rolled back.
            sendAllDataToRemote();
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }
}
