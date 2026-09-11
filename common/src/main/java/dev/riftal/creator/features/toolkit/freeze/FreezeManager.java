package dev.riftal.creator.features.toolkit.freeze;

import dev.riftal.creator.core.sched.ScheduledTask;
import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import dev.riftal.creator.features.toolkit.net.FreezeStatePayload;
import dev.riftal.creator.features.toolkit.net.ToolkitNet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The two freezes, and the vanilla third one.
 *
 * <ul>
 *   <li><b>mobs</b> - {@code ToolkitServerLevelMixin} cancels {@code ServerLevel#tickNonPassenger}
 *       (and {@code tickPassenger}, so a mob in a boat stops too) for every {@link Mob}. Nothing is
 *       written to entity NBT, so nothing has to be undone: pose, position, age, fire and despawn
 *       counters all simply stop. Players, item entities, projectiles and mobs a player is riding
 *       are never frozen.</li>
 *   <li><b>players</b> - non-op players are pinned to the spot they stood on: zero velocity, hidden
 *       slowness 255 and a jump-strength modifier of -1 so the client cannot even start a walk or a
 *       jump, plus a snap-back teleport if they drift anyway. Ops stay mobile, because the director
 *       is an op.</li>
 *   <li><b>all</b> - vanilla {@code /tick freeze} through {@code ServerTickRateManager}. Freezes
 *       block entities, weather and daylight too, which is why it is opt-in.</li>
 * </ul>
 */
public final class FreezeManager {

    /** Owner tag for the player-lock task, so cancelling it never touches another feature's work. */
    public static final ResourceLocation TASK = ResourceLocation.fromNamespaceAndPath(
            ToolkitFeature.NAMESPACE, "player_lock");

    /**
     * Id of the jump-strength modifier a locked player carries. Transient, so it is never written
     * to the player's NBT: a crash while frozen cannot leave a crew member unable to jump.
     */
    public static final ResourceLocation JUMP_LOCK = ResourceLocation.fromNamespaceAndPath(
            ToolkitFeature.NAMESPACE, "player_lock_jump");

    /** How far a locked player may drift before we snap them back, squared. */
    private static final double DRIFT_SQR = 0.01D * 0.01D;

    /** Re-applied every second; long enough to cover the gap, short enough to expire on release. */
    private static final int SLOWNESS_TICKS = 40;

    /** The amplifier our slowness uses. Also how {@link #release} recognises its own effect. */
    private static final int SLOWNESS_AMPLIFIER = 255;

    /**
     * Where one locked player is pinned, and in which dimension.
     *
     * <p>The dimension is the whole point: a bare {@code Vec3} survives a death, a respawn or a
     * {@code /toolkit tphere} - all of which build or move a {@code ServerPlayer} with the same
     * UUID - and the next tick would then teleport the player to those coordinates in whatever
     * dimension they are now standing in.
     */
    private record Lock(ResourceKey<Level> dimension, Vec3 position) {
    }

    private static volatile boolean mobsFrozen;
    private static boolean playersFrozen;

    private static final Map<UUID, Lock> LOCKS = new HashMap<>();
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
        Set<UUID> released = Set.copyOf(LOCKS.keySet());
        playersFrozen = frozen;
        LOCKS.clear();
        lockTick = 0;
        if (frozen) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.hasPermissions(2)) {
                    LOCKS.put(player.getUUID(), lockAt(player));
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

    /**
     * Drops one player's lock without releasing the freeze itself.
     *
     * <p>Called on respawn and on disconnect. The lock is re-taken where the player next stands, so
     * a crew member who dies in the Nether is pinned at their respawn point instead of being
     * teleported, every tick, into overworld terrain at their Nether coordinates.
     */
    public static void forget(UUID player) {
        if (player != null) {
            LOCKS.remove(player);
        }
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

    private static Lock lockAt(ServerPlayer player) {
        return new Lock(player.level().dimension(), player.position());
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
            if (!player.isAlive()) {
                // Dead, waiting on the respawn screen: pinning a corpse only makes the death
                // camera judder, and the respawned player gets a fresh lock anyway.
                continue;
            }
            Lock lock = LOCKS.get(player.getUUID());
            if (lock == null || !lock.dimension().equals(player.level().dimension())) {
                // Either a crew member who joined - or relogged, or respawned, or was pulled here
                // by /toolkit tphere - after the freeze went on, or one who changed dimension while
                // locked. Ops are still never locked: the director has to be able to walk the set.
                if (player.hasPermissions(2)) {
                    continue;
                }
                lock = lockAt(player);
                LOCKS.put(player.getUUID(), lock);
            }
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            player.stopFallFlying();
            applyJumpLock(player);
            if (refreshEffect) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOWNESS_TICKS,
                        SLOWNESS_AMPLIFIER, true, false, false));
            }
            Vec3 pinned = lock.position();
            if (player.position().distanceToSqr(pinned) > DRIFT_SQR) {
                player.connection.teleport(pinned.x, pinned.y, pinned.z,
                        player.getYRot(), player.getXRot());
            }
        }
    }

    /**
     * Slowness 255 stops walking but does nothing to jumping, so without this a locked crew member
     * can still hop - and lands outside the 0.01-block drift budget, which teleports them back
     * every single tick. That rubber-band is the artefact the plan calls out; -1.0 ADD_VALUE on
     * {@code JUMP_STRENGTH} removes the cause instead of fighting the symptom.
     */
    private static void applyJumpLock(ServerPlayer player) {
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump == null || jump.hasModifier(JUMP_LOCK)) {
            return;
        }
        jump.addTransientModifier(new AttributeModifier(JUMP_LOCK, -1.0D,
                AttributeModifier.Operation.ADD_VALUE));
    }

    private static void clearJumpLock(ServerPlayer player) {
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            jump.removeModifier(JUMP_LOCK);
        }
    }

    /**
     * Undoes everything {@link #tickLocks} applied - but only where it is provably ours. A crew
     * member who drank a slowness potion, or walked into powder snow, during a locked shot must
     * still be slow when the freeze lifts, so the effect is removed only when its amplifier and
     * visibility match the one we add.
     */
    private static void release(MinecraftServer server, Set<UUID> released) {
        if (server == null || released.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!released.contains(player.getUUID())) {
                continue;
            }
            clearJumpLock(player);
            MobEffectInstance existing = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (existing != null && existing.getAmplifier() == SLOWNESS_AMPLIFIER
                    && !existing.isVisible()) {
                player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
        }
    }

    private FreezeManager() {
    }
}
