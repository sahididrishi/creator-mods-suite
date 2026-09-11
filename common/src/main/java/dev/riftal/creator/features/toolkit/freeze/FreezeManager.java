package dev.riftal.creator.features.toolkit.freeze;

import dev.riftal.creator.core.sched.ScheduledTask;
import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import dev.riftal.creator.features.toolkit.net.FreezeStatePayload;
import dev.riftal.creator.features.toolkit.net.ToolkitNet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The two freezes, and the vanilla third one.
 *
 * <ul>
 *   <li><b>mobs</b> - {@code ToolkitServerLevelMixin} cancels {@code ServerLevel#tickNonPassenger}
 *       for every {@link Mob}. Nothing is written to entity NBT, so nothing has to be undone: pose,
 *       position, age, fire and despawn counters all simply stop. Players, item entities,
 *       projectiles and mobs a player is riding are never frozen.</li>
 *   <li><b>players</b> - non-op players are pinned to the spot they stood on: zero velocity, hidden
 *       slowness 255 so the client does not even start a walk animation, and a snap-back teleport
 *       if they drift. Ops stay mobile, because the director is an op.</li>
 *   <li><b>all</b> - vanilla {@code /tick freeze} through {@code ServerTickRateManager}. Freezes
 *       block entities, weather and daylight too, which is why it is opt-in.</li>
 * </ul>
 */
public final class FreezeManager {

    /** Owner tag for the player-lock task, so cancelling it never touches another feature's work. */
    public static final ResourceLocation TASK = ResourceLocation.fromNamespaceAndPath(
            ToolkitFeature.NAMESPACE, "player_lock");

    /** How far a locked player may drift before we snap them back, squared. */
    private static final double DRIFT_SQR = 0.01D * 0.01D;

    /** Re-applied every second; long enough to cover the gap, short enough to expire on release. */
    private static final int SLOWNESS_TICKS = 40;

    private static volatile boolean mobsFrozen;
    private static boolean playersFrozen;

    private static final Map<UUID, Vec3> LOCKS = new HashMap<>();
    private static ScheduledTask lockTask;
    private static int lockTick;

    /** Hot path: called once per entity per tick from the mixin, so it stays a field read. */
    public static boolean isFrozen(Entity entity) {
        if (!mobsFrozen) {
            return false;
        }
        if (!(entity instanceof Mob)) {
            return false;
        }
        return !entity.hasPassenger(passenger -> passenger instanceof Player);
    }

    public static boolean mobsFrozen() {
        return mobsFrozen;
    }

    public static boolean playersFrozen() {
        return playersFrozen;
    }

    /** True when vanilla's whole-game freeze is on. */
    public static boolean allFrozen(MinecraftServer server) {
        return server != null && server.tickRateManager().isFrozen();
    }

    /** Turns the mob tick-cancel on or off. */
    public static void setMobs(MinecraftServer server, boolean frozen) {
        mobsFrozen = frozen;
        broadcast(server);
    }

    /**
     * Locks or releases every non-op player. Anyone who joins while the freeze is on is locked
     * where they land, on the next tick; ops are never locked.
     */
    public static void setPlayers(MinecraftServer server, boolean frozen) {
        java.util.Set<UUID> released = java.util.Set.copyOf(LOCKS.keySet());
        playersFrozen = frozen;
        LOCKS.clear();
        lockTick = 0;
        if (frozen) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.hasPermissions(2)) {
                    LOCKS.put(player.getUUID(), player.position());
                }
            }
            startTask();
        } else {
            stopTask();
            release(server, released);
        }
        broadcast(server);
    }

    /** How many players the last {@link #setPlayers} actually locked. */
    public static int lockedCount() {
        return LOCKS.size();
    }

    /** Vanilla {@code /tick freeze} - everything, including weather and block entities. */
    public static void setAll(MinecraftServer server, boolean frozen) {
        server.tickRateManager().setFrozen(frozen);
    }

    /** Drops every flag and cancels the lock task. Called when the server this state belongs to goes away. */
    public static void reset() {
        mobsFrozen = false;
        playersFrozen = false;
        LOCKS.clear();
        lockTick = 0;
        stopTask();
    }

    /** Re-sends the current flags, e.g. to a player who just asked for a sync. */
    public static FreezeStatePayload payload() {
        return new FreezeStatePayload(mobsFrozen, playersFrozen);
    }

    private static void broadcast(MinecraftServer server) {
        ToolkitNet.sendAll(server, payload());
    }

    private static void startTask() {
        if (lockTask != null && !lockTask.isDone()) {
            return;
        }
        lockTask = TickScheduler.runRepeating(1, -1, FreezeManager::tickLocks).tag(TASK);
    }

    private static void stopTask() {
        if (lockTask != null) {
            lockTask.cancel();
            lockTask = null;
        }
        TickScheduler.cancelAll(TASK);
    }

    private static void tickLocks(ScheduledTask task) {
        MinecraftServer server = TickScheduler.server();
        if (server == null || !playersFrozen) {
            task.cancel();
            lockTask = null;
            return;
        }
        boolean refreshEffect = lockTick++ % 20 == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Vec3 lock = LOCKS.get(player.getUUID());
            if (lock == null) {
                // A crew member who joined - or relogged - after the freeze went on. Ops are still
                // never locked: the director has to be able to walk the set.
                if (player.hasPermissions(2)) {
                    continue;
                }
                lock = player.position();
                LOCKS.put(player.getUUID(), lock);
            }
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            player.stopFallFlying();
            if (refreshEffect) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOWNESS_TICKS, 255,
                        true, false, false));
            }
            if (player.position().distanceToSqr(lock) > DRIFT_SQR) {
                player.connection.teleport(lock.x, lock.y, lock.z, player.getYRot(), player.getXRot());
            }
        }
    }

    private static void release(MinecraftServer server, java.util.Set<UUID> released) {
        if (server == null || released.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (released.contains(player.getUUID())) {
                player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
        }
    }

    private FreezeManager() {
    }
}
