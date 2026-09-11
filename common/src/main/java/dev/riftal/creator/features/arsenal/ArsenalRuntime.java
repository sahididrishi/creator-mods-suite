package dev.riftal.creator.features.arsenal;

import dev.riftal.creator.features.arsenal.item.SoulScytheItem;
import dev.riftal.creator.features.arsenal.mechanic.GrappleManager;
import dev.riftal.creator.features.arsenal.mechanic.GravitySlam;
import dev.riftal.creator.features.arsenal.mechanic.SoulWisp;
import net.minecraft.server.MinecraftServer;

/**
 * Owns the boundary between one server session and the next.
 *
 * <p>Four of this feature's mechanics keep transient per-player state in static maps, exactly as
 * plan 07 section 5 ("Persistence") specifies - no attachments, nothing written to disk. What the
 * plan does not say, and what was missing, is that those maps have to be emptied when the world
 * they belong to closes:
 *
 * <ul>
 *   <li>{@code GrappleManager.HOOKS} holds a live {@code GrappleHookEntity}, and an entity holds its
 *       {@code Level};</li>
 *   <li>{@code GravitySlam.ACTIVE} holds a {@code ServerLevel} field outright, plus lists of the
 *       mobs it lifted and the {@code ServerPlayer} that swung;</li>
 *   <li>{@code SoulScytheItem.PENDING} holds one snapshot per player who ever swung the scythe;</li>
 *   <li>{@code SoulWisp} holds per-player sound bookkeeping and scheduled flights.</li>
 * </ul>
 *
 * <p>In single player that means the whole previous world stays reachable while the creator plays
 * the next one - a recording-session memory problem, not a theoretical one. Worse,
 * {@code GravitySlam.start} calls {@code cancel(player)} first, so the first hammer swing in the new
 * world would iterate the stale {@code lifted} list and call {@code removeEffect} on mobs belonging
 * to a closed level.
 *
 * <p>Core's own stop hook clears only {@code TickScheduler} and silent mode, and the loader
 * bootstraps are shared files no feature may edit, so the signal comes from
 * {@code ArsenalMinecraftServerMixin} on {@code MinecraftServer#stopServer} - the same route the
 * {@code toolkit}, {@code powers} and {@code rules} features take.
 */
public final class ArsenalRuntime {

    /** Called from the shutdown mixin. Safe to call more than once and with a null server. */
    public static void onServerStopping(MinecraftServer server) {
        GrappleManager.reset();
        GravitySlam.reset();
        SoulWisp.reset();
        SoulScytheItem.reset();
    }

    private ArsenalRuntime() {
    }
}
