package dev.riftal.creator.features.rules.rules;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.core.util.Titles;
import dev.riftal.creator.features.rules.api.Rule;
import dev.riftal.creator.features.rules.api.RuleContext;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Minecraft but every minute the game gives or takes an item.</b>
 *
 * <p>A coin flip per player: either a random item lands in the inventory (a full stack a quarter of
 * the time) or one random occupied slot is emptied. Either way a title card says what happened,
 * which is the shot - the rule exists to be seen.
 *
 * <p>The next roll is stored as an absolute game time in the world's saved data, so a relog does
 * not reset the clock and a restart does not fire a roll immediately.
 */
public final class ItemRouletteRule implements Rule {

    /** Ticks between rolls: 60 seconds. */
    public static final int INTERVAL_TICKS = 1200;

    /** Chance a "give" hands over a whole stack rather than one item. */
    public static final float FULL_STACK_CHANCE = 0.25F;

    /** Main inventory slots the roulette may empty: hotbar plus the three rows. */
    public static final int MAIN_SLOTS = 36;

    private long nextRollTick = -1L;

    @Override
    public String id() {
        return "item_roulette";
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    @Override
    public void onEnable(RuleContext ctx) {
        long now = ctx.gameTime();
        if (nextRollTick <= 0L || nextRollTick > now + INTERVAL_TICKS) {
            nextRollTick = now + INTERVAL_TICKS;
        }
    }

    @Override
    public void tick(RuleContext ctx) {
        long now = ctx.gameTime();
        if (nextRollTick <= 0L) {
            nextRollTick = now + INTERVAL_TICKS;
            return;
        }
        if (now < nextRollTick) {
            return;
        }
        nextRollTick = now + INTERVAL_TICKS;
        for (ServerPlayer player : ctx.players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            roll(player, ctx.random());
        }
    }

    /** One roll for one player. Public so the GameTest can force it. */
    public static void roll(ServerPlayer player, RandomSource random) {
        boolean give = random.nextBoolean();
        if (!give && !take(player, random)) {
            give = true;
        }
        if (give) {
            give(player, random);
        }
    }

    private static void give(ServerPlayer player, RandomSource random) {
        Item item = RandomItems.pick(random);
        if (item == Items.AIR) {
            return;
        }
        ItemStack stack = new ItemStack(item);
        if (stack.isStackable() && random.nextFloat() < FULL_STACK_CHANCE) {
            stack.setCount(stack.getMaxStackSize());
        }
        Component label = describe(stack);
        player.getInventory().placeItemBackInInventory(stack.copy());
        Titles.show(player,
                Component.translatable("hud.creator_rules.roulette").withStyle(ChatFormatting.GOLD),
                Component.translatable("hud.creator_rules.roulette.give", label)
                        .withStyle(ChatFormatting.GREEN),
                5, 40, 10);
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    private static boolean take(ServerPlayer player, RandomSource random) {
        Inventory inventory = player.getInventory();
        List<Integer> occupied = new ArrayList<>();
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            if (!inventory.getItem(slot).isEmpty()) {
                occupied.add(slot);
            }
        }
        if (occupied.isEmpty()) {
            return false;
        }
        int slot = occupied.get(random.nextInt(occupied.size()));
        ItemStack removed = inventory.getItem(slot).copy();
        inventory.setItem(slot, ItemStack.EMPTY);
        Titles.show(player,
                Component.translatable("hud.creator_rules.roulette").withStyle(ChatFormatting.GOLD),
                Component.translatable("hud.creator_rules.roulette.take", describe(removed))
                        .withStyle(ChatFormatting.RED),
                5, 40, 10);
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.ITEM_BREAK,
                SoundSource.PLAYERS, 0.8F, 0.9F);
        return true;
    }

    private static Component describe(ItemStack stack) {
        if (stack.getCount() > 1) {
            return Component.empty()
                    .append(stack.getHoverName())
                    .append(Component.literal(" x" + stack.getCount()));
        }
        return stack.getHoverName().copy();
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putLong("nextRollTick", nextRollTick);
    }

    @Override
    public void load(CompoundTag tag) {
        nextRollTick = tag.contains("nextRollTick") ? tag.getLong("nextRollTick") : -1L;
    }

    /** Absolute game time of the next roll, or -1 before the first one is scheduled. */
    public long nextRollTick() {
        return nextRollTick;
    }
}
