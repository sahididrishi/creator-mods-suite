package dev.riftal.creator.features.rules.rules;

import dev.riftal.creator.features.rules.api.Rule;
import dev.riftal.creator.features.rules.api.RuleContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * <b>Minecraft but every craft yields ten times as much.</b>
 *
 * <p>The stack the player actually picked up is untouched; the other nine copies are pushed into
 * the inventory (and drop at their feet when it is full), which keeps the vanilla pick-up and
 * shift-click flows working unchanged.
 *
 * <p>Driven from a {@code ResultSlot#onTake} mixin, so the crafting table, the 2x2 inventory grid
 * and shift-clicking all go through the same path, while stonecutters and smithing tables (which
 * use different slot classes) are deliberately unaffected.
 */
public final class CraftsX10Rule implements Rule {

    /** Total multiplier including the stack the player already has. */
    public static final int MULTIPLIER = 10;

    /** Refuse to insert more than this many extra stacks from one craft; a sanity valve. */
    private static final int MAX_EXTRA_STACKS = 64;

    @Override
    public String id() {
        return "crafts_x10";
    }

    @Override
    public void onCraftTaken(RuleContext ctx, ServerPlayer player, ItemStack result) {
        multiply(player, result);
    }

    /**
     * Pushes the nine extra copies into the inventory. Public and typed on {@code Player} so the
     * GameTest can call it with a mock player that is not in the player list.
     */
    public static void multiply(Player player, ItemStack result) {
        if (result.isEmpty()) {
            return;
        }
        int extra = result.getCount() * (MULTIPLIER - 1);
        int stacksInserted = 0;
        while (extra > 0 && stacksInserted < MAX_EXTRA_STACKS) {
            ItemStack copy = result.copy();
            int count = Math.min(extra, Math.max(1, copy.getMaxStackSize()));
            copy.setCount(count);
            player.getInventory().placeItemBackInInventory(copy);
            extra -= count;
            stacksInserted++;
        }
    }
}
