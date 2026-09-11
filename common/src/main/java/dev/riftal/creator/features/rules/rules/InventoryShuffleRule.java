package dev.riftal.creator.features.rules.rules;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.rules.api.Rule;
import dev.riftal.creator.features.rules.api.RuleContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Minecraft but your inventory reshuffles every 30 seconds.</b>
 *
 * <p>Only the 36 main slots move; armour and off-hand are left alone so the shuffle cannot silently
 * unequip anyone mid-fight. The sword is never where the creator left it, which is the joke.
 *
 * <p>Like {@code item_roulette} the next shuffle is an absolute game time in the saved data, so the
 * clock keeps running across a restart.
 */
public final class InventoryShuffleRule implements Rule {

    /** Ticks between shuffles: 30 seconds. */
    public static final int INTERVAL_TICKS = 600;

    /** Slots the shuffle touches: the hotbar plus the three inventory rows. Armour is not included. */
    public static final int MAIN_SLOTS = 36;

    private long nextShuffleTick = -1L;

    @Override
    public String id() {
        return "inventory_shuffle";
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    @Override
    public void onEnable(RuleContext ctx) {
        long now = ctx.gameTime();
        if (nextShuffleTick <= 0L || nextShuffleTick > now + INTERVAL_TICKS) {
            nextShuffleTick = now + INTERVAL_TICKS;
        }
    }

    @Override
    public void tick(RuleContext ctx) {
        long now = ctx.gameTime();
        if (nextShuffleTick <= 0L) {
            nextShuffleTick = now + INTERVAL_TICKS;
            return;
        }
        if (now < nextShuffleTick) {
            return;
        }
        nextShuffleTick = now + INTERVAL_TICKS;
        for (ServerPlayer player : ctx.players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            shuffle(player, ctx.random());
            Fx.sound(player.serverLevel(), player.position(), SoundEvents.NOTE_BLOCK_PLING.value(),
                    SoundSource.PLAYERS, 0.7F, 1.4F);
        }
    }

    /**
     * Fisher-Yates over the 36 main slots. Public, and typed on {@link Player} rather than
     * {@code ServerPlayer}, so the GameTest can drive it with a mock player that is not in the
     * player list.
     */
    public static void shuffle(Player player, RandomSource random) {
        Inventory inventory = player.getInventory();
        List<ItemStack> stacks = new ArrayList<>(MAIN_SLOTS);
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            stacks.add(inventory.getItem(slot));
        }
        for (int i = stacks.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            ItemStack swap = stacks.get(i);
            stacks.set(i, stacks.get(j));
            stacks.set(j, swap);
        }
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            inventory.setItem(slot, stacks.get(slot));
        }
        player.containerMenu.broadcastChanges();
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putLong("nextShuffleTick", nextShuffleTick);
    }

    @Override
    public void load(CompoundTag tag) {
        nextShuffleTick = tag.contains("nextShuffleTick") ? tag.getLong("nextShuffleTick") : -1L;
    }

    /** Absolute game time of the next shuffle, or -1 before the first is scheduled. */
    public long nextShuffleTick() {
        return nextShuffleTick;
    }
}
