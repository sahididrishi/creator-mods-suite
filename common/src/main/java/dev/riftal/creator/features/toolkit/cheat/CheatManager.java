package dev.riftal.creator.features.toolkit.cheat;

import dev.riftal.creator.core.data.PlayerData;
import dev.riftal.creator.core.sched.ScheduledTask;
import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;

/**
 * God, fly, heal and clear.
 *
 * <p>{@code god} and {@code fly} are sticky: they live in a per-player attachment, so they survive a
 * death and a relog. Vanilla resets {@link Abilities} on respawn, so they are re-asserted from three
 * places: the player-join hook, the respawn hook, and a slow repeating task as a backstop - which
 * only runs while at least one online player actually has a cheat on, and runs once a second, never
 * per tick.
 *
 * <p>The two hooks are not belt and braces, they are the correctness. The task used to be armed
 * only by the command itself, so a director who set god in a previous session had no task at all in
 * this one: the attachment still said {@code god=true}, the HUD still said ON, and the first death
 * quietly turned it off. Even with the task armed, waiting up to a second after every respawn is a
 * second of being killable on camera.
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
        applyGod(player, value);
        armReapply();
        return value;
    }

    /** Turns creative flight on or off; switching it off also drops the player out of the air. */
    public static boolean fly(ServerPlayer player, boolean value) {
        store(player, get(player).withFly(value));
        applyFly(player, value);
        armReapply();
        return value;
    }

    /**
     * Writes the god ability and pushes it to the client, without touching the stored flag.
     *
     * <p>Takes a {@link Player} rather than a {@code ServerPlayer} so the GameTest for this can run
     * against {@code GameTestHelper#makeMockPlayer}, which returns a plain {@code Player}.
     * {@code Player#onUpdateAbilities()} is a no-op on the base class and sends
     * {@code ClientboundPlayerAbilitiesPacket} on {@code ServerPlayer}.
     */
    public static void applyGod(Player player, boolean value) {
        Abilities abilities = player.getAbilities();
        abilities.invulnerable = value;
        player.onUpdateAbilities();
    }

    /** Writes the fly ability and pushes it to the client, without touching the stored flag. */
    public static void applyFly(Player player, boolean value) {
        Abilities abilities = player.getAbilities();
        abilities.mayfly = value;
        if (!value) {
            abilities.flying = false;
        }
        player.onUpdateAbilities();
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

    /**
     * A player finished loading in: put their stored cheats back and make sure the re-apply task is
     * running.
     *
     * <p>Without this the task only ever existed if the director typed {@code /toolkit cheat} in
     * <em>this</em> session. The attachment persists {@code god=true} across a restart and
     * {@code Abilities.invulnerable} comes back from player NBT, so god looked on - and then the
     * first death rebuilt {@code Abilities} from the game mode and silently switched it off, with
     * nothing left to notice.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        reapply(player);
        if (get(player).any()) {
            armReapply();
        }
    }

    /**
     * A death produced a fresh {@code ServerPlayer} whose {@link Abilities} came from the game mode.
     * Re-assert on the same tick rather than leaving a full second of vulnerability for the
     * repeating task to close.
     */
    public static void onPlayerRespawn(ServerPlayer player) {
        reapply(player);
        if (get(player).any()) {
            armReapply();
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
