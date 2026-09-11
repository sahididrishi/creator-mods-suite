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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The {@code /shop} window: a plain three-row chest on the client (so no screen class, no menu type
 * and no client code at all) whose slots are read-only price tags on the server.
 *
 * <p>This is the only place in the whole feature where the client drives anything, so every click
 * is treated as hostile input:
 *
 * <ul>
 *   <li>{@link #clicked} buys <b>only</b> on a plain left click ({@link ClickType#PICKUP} with
 *       button 0). The vanilla drag protocol sends a {@code QUICK_CRAFT} click carrying the real
 *       slot id for every slot the cursor crosses, so treating every click type as a purchase meant
 *       one click-drag across the top row bought a whole row - several hearts of maximum health for
 *       a gesture the creator never meant to make. Middle-click ({@code CLONE}), {@code THROW} and
 *       hotbar {@code SWAP} were purchases too.</li>
 *   <li>A {@code QUICK_CRAFT} click resets the server's half-built drag state instead of being
 *       silently swallowed.</li>
 *   <li>{@link #quickMoveStack} disables shift-click outright, so nothing can be posted into a
 *       container that is not backed by anything.</li>
 *   <li>{@link #canTakeItemForPickAll} refuses the double-click "gather all" sweep, which walks
 *       the slots directly and never goes through {@link #clicked} at all.</li>
 * </ul>
 *
 * <p>Every purchase is still re-validated in {@link HeartsCurrencyRule#purchase} - rule active,
 * offer real, price affordable - because this class cannot be the only gate.
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

    /**
     * Double-clicking a stack in the player's inventory makes vanilla sweep every slot in the menu,
     * calling {@code mayPickup}/{@code remove} directly. The display stacks only survived that by
     * accident (their price lore makes them compare unequal to anything obtainable); this makes it
     * a rule.
     */
    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != getContainer() && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < ShopOffers.SLOTS) {
            if (clickType == ClickType.QUICK_CRAFT) {
                resetQuickCraft();
            } else if (clickType == ClickType.PICKUP && button == 0
                    && player instanceof ServerPlayer serverPlayer && slotId < offers.size()) {
                HeartsCurrencyRule.purchase(serverPlayer, offers.get(slotId));
            }
            // Re-send the whole window so the client's optimistic move is rolled back.
            sendAllDataToRemote();
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }
}
