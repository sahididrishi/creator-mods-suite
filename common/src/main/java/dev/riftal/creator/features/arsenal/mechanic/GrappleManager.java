package dev.riftal.creator.features.arsenal.mechanic;

import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.features.arsenal.ArsenalFeature;
import dev.riftal.creator.features.arsenal.entity.GrappleHookEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side bookkeeping for the Grapple Blade: one live hook and one live pull per player.
 *
 * <p>All state is transient - nothing is persisted and nothing is attached to the player, exactly
 * as plan 07 specifies. Each pull owns a repeating {@code TickScheduler} task tagged
 * {@code creator_arsenal:grapple} which cancels itself when the pull completes, so the feature has
 * no always-on tick loop.
 */
public final class GrappleManager {

    /** Scheduler tag for every grapple task, so {@code /arsenal hook retract} can wipe them. */
    public static final ResourceLocation TASK_TAG = ArsenalFeature.res("grapple");

    private static final Map<UUID, GrappleHookEntity> HOOKS = new HashMap<>();
    private static final Map<UUID, GrapplePull> PULLS = new HashMap<>();

    /** The player's live hook, or null. Removed hooks are forgotten lazily. */
    public static GrappleHookEntity hookOf(Player player) {
        GrappleHookEntity hook = HOOKS.get(player.getUUID());
        if (hook != null && hook.isRemoved()) {
            HOOKS.remove(player.getUUID());
            return null;
        }
        return hook;
    }

    /** True when the player already has a hook in the world. */
    public static boolean hasHook(Player player) {
        return hookOf(player) != null;
    }

    /** Records a freshly fired hook, replacing (and retracting) any previous one. */
    public static void setHook(Player player, GrappleHookEntity hook) {
        GrappleHookEntity previous = hookOf(player);
        if (previous != null && previous != hook) {
            previous.retract();
        }
        HOOKS.put(player.getUUID(), hook);
    }

    /** Called by the hook when it leaves the world. */
    public static void onHookRemoved(Player player, GrappleHookEntity hook) {
        UUID id = player.getUUID();
        if (HOOKS.get(id) == hook) {
            HOOKS.remove(id);
        }
        GrapplePull pull = PULLS.get(id);
        if (pull != null && pull.hook() == hook) {
            pull.stopPulling();
        }
    }

    /** Starts reeling {@code player} to {@code hook}. Replaces any pull already running. */
    public static void beginPull(ServerPlayer player, GrappleHookEntity hook) {
        UUID id = player.getUUID();
        GrapplePull pull = new GrapplePull(hook);
        PULLS.put(id, pull);

        MinecraftServer server = player.server;
        TickScheduler.runRepeating(1, -1, task -> {
            GrapplePull current = PULLS.get(id);
            if (current != pull) {
                task.cancel();
                return;
            }
            ServerPlayer live = server.getPlayerList().getPlayer(id);
            if (live == null || !live.isAlive() || live.level() != hook.level()) {
                PULLS.remove(id);
                if (!hook.isRemoved()) {
                    hook.retract();
                }
                task.cancel();
                return;
            }
            boolean wasPulling = current.isPulling();
            boolean done = current.tick(live);
            if (wasPulling && !current.isPulling() && !hook.isRemoved()) {
                hook.retract();
            }
            if (done) {
                PULLS.remove(id);
                task.cancel();
            }
        }).tag(TASK_TAG);
    }

    /**
     * Ends the velocity phase of the player's pull but leaves the task alive for its fall-damage
     * grace window. This is what cutting the line does - the creator should still land softly.
     */
    public static void stopPull(Player player) {
        GrapplePull pull = PULLS.get(player.getUUID());
        if (pull != null) {
            pull.stopPulling();
        }
    }

    /** Drops the pull state entirely, grace window included. Used by {@code /arsenal hook retract}. */
    public static void cancelPull(Player player) {
        PULLS.remove(player.getUUID());
    }

    /** True while the player is being reeled in or still inside the safe-landing window. */
    public static boolean isPulling(Player player) {
        return PULLS.containsKey(player.getUUID());
    }

    /**
     * Recording control: discard the player's hook and forget the pull. Used by
     * {@code /arsenal hook retract} to un-stick the creator on camera.
     *
     * @return true if there was something to clear
     */
    public static boolean clear(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean had = false;
        GrappleHookEntity hook = HOOKS.remove(id);
        if (hook != null && !hook.isRemoved()) {
            hook.discard();
            had = true;
        }
        had |= PULLS.remove(id) != null;
        return had;
    }

    private GrappleManager() {
    }
}
