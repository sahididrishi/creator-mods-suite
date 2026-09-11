package dev.riftal.creator.features.toolkit.cheat;

import dev.riftal.creator.core.data.PlayerData;
import dev.riftal.creator.core.sched.ScheduledTask;
import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

/**
 * God, fly, heal and clear.
 *
 * <p>{@code god} and {@code fly} are sticky: they live in a per-player attachment, so they survive a
 * death and a relog. Vanilla resets {@link Abilities} on respawn, so a slow repeating task re-applies
 * them - it only runs while at least one player actually has a cheat on, and it runs once a second,
 * never per tick.
 */
public final class CheatManager {

    /** Owner tag for the re-apply task. */
    public static final ResourceLocation TASK = ResourceLocation.fromNamespaceAndPath(
            ToolkitFeature.NAMESPACE, "cheats");

    /** How often the sticky cheats are re-asserted, in ticks. */
    private static final int REAPPLY_PERIOD = 20;

    /** Registered in {@code ToolkitFeature#registerContent()}; never read from a static initialiser. */
    public static PlayerData<CheatFlags> flags;

    private static ScheduledTask reapplyTask;

    /** Current flags for one player. */
    public static CheatFlags get(ServerPlayer player) {
        return flags == null ? CheatFlags.NONE : flags.get(player);
    }

    /** Turns invulnerability on or off and pushes the change to the client. */
    public static boolean god(ServerPlayer player, boolean value) {
        store(player, get(player).withGod(value));
        Abilities abilities = player.getAbilities();
        abilities.invulnerable = value;
        player.onUpdateAbilities();
        armReapply();
        return value;
    }

    /** Turns creative flight on or off; switching it off also drops the player out of the air. */
    public static boolean fly(ServerPlayer player, boolean value) {
        store(player, get(player).withFly(value));
        Abilities abilities = player.getAbilities();
        abilities.mayfly = value;
        if (!value) {
            abilities.flying = false;
        }
        player.onUpdateAbilities();
        armReapply();
        return value;
    }

    /** Full restore: health, hunger, saturation, effects, fire and air. */
    public static void heal(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        player.removeAllEffects();
        player.clearFire();
        player.setAirSupply(player.getMaxAirSupply());
    }

    /** Empties the inventory. Returns nothing useful - it either happened or the player was null. */
    public static void clearInventory(ServerPlayer player) {
        player.getInventory().clearContent();
    }

    /** Re-applies the stored abilities, e.g. after a respawn wiped them. */
    public static void reapply(ServerPlayer player) {
        CheatFlags stored = get(player);
        Abilities abilities = player.getAbilities();
        boolean changed = false;
        if (stored.god() && !abilities.invulnerable) {
            abilities.invulnerable = true;
            changed = true;
        }
        if (stored.fly() && !abilities.mayfly) {
            abilities.mayfly = true;
            changed = true;
        }
        if (changed) {
            player.onUpdateAbilities();
        }
    }

    /** Drops the re-apply task. The attachment itself is per player and persists on its own. */
    public static void reset() {
        if (reapplyTask != null) {
            reapplyTask.cancel();
            reapplyTask = null;
        }
        TickScheduler.cancelAll(TASK);
    }

    private static void store(ServerPlayer player, CheatFlags value) {
        if (flags != null) {
            flags.set(player, value);
        }
    }

    private static void armReapply() {
        if (reapplyTask != null && !reapplyTask.isDone()) {
            return;
        }
        reapplyTask = TickScheduler.runRepeating(REAPPLY_PERIOD, -1, CheatManager::tick).tag(TASK);
    }

    private static void tick(ScheduledTask task) {
        MinecraftServer server = TickScheduler.server();
        if (server == null) {
            task.cancel();
            reapplyTask = null;
            return;
        }
        boolean anyone = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (get(player).any()) {
                anyone = true;
                reapply(player);
            }
        }
        if (!anyone) {
            task.cancel();
            reapplyTask = null;
        }
    }

    private CheatManager() {
    }
}
