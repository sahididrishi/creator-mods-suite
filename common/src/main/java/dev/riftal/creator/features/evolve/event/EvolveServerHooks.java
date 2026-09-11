package dev.riftal.creator.features.evolve.event;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.data.EvolutionData;
import dev.riftal.creator.features.evolve.perk.StagePerks;
import dev.riftal.creator.features.evolve.progression.EvolveManager;
import dev.riftal.creator.features.evolve.progression.Transformation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Evolve's server-side heartbeat, and the entry points the mixins and the loader glue call into.
 *
 * <p>Core exposes no join / respawn / dimension-change events, so instead of three loader-specific
 * hooks there is one five-tick task that walks the (tiny) online player list. A player who
 * "appears" - first join, respawn after death, a relog, or a trip through a portal - gets their
 * modifiers re-applied, any interrupted transformation resolved, and the whole roster re-sent so
 * their HUD and the beast model are correct immediately.
 *
 * <p><b>What counts as appearing.</b> {@link Seen} remembers the {@code ServerPlayer} <em>instance</em>
 * and the dimension it was in, and both are load-bearing on 1.21.1:
 * <ul>
 *   <li>{@code PlayerList#respawn} ends with {@code serverplayer.setId(player.getId())}, so a
 *       respawned player is a brand new object carrying the <em>old</em> entity id. An id-only
 *       check never fires on a death - and {@code Player#restoreFrom} does not copy attribute
 *       modifiers on the death path, so the body would never be put back on.</li>
 *   <li>{@code ServerPlayer#changeDimension} does the opposite: same instance, same id, different
 *       level. An instance-only check never fires on a nether trip - while the client throws its
 *       {@code ClientLevel} away and wipes {@code ClientEvolutionCache} with it, leaving an Apex
 *       player rendering as their own skin at 2.6x with a Hatchling HUD.</li>
 * </ul>
 * The reference is weak so a dead {@code ServerPlayer} is never pinned by this map.
 *
 * <p><b>What a thrown exception costs.</b> {@code TickScheduler} cancels a task that throws three
 * times in a row, and this heartbeat is the feature's only lifecycle hook - losing it means no
 * re-apply, no syncs, no perks and no transformation resume for the rest of the session. Every
 * per-player step is therefore wrapped: one player in a bad state is logged and skipped, and the
 * other nineteen keep working.
 *
 * <p>The five-second roster re-sync stays as a backstop. The real fix for "someone walked into
 * view" is {@link #onStartTracking}, wired per loader.
 */
public final class EvolveServerHooks {

    /** Scheduler tag for the heartbeat, so it can be cancelled and re-armed on its own. */
    public static final ResourceLocation HEARTBEAT_TAG = EvolveFeature.id("heartbeat");

    /** Heartbeats between full roster re-syncs. 20 heartbeats x 5 ticks = 5 seconds. */
    private static final int RESYNC_EVERY = 20;

    /** Which {@code ServerPlayer} instance, in which dimension, we last handled for a player. */
    private static final class Seen {

        private final WeakReference<ServerPlayer> instance;

        private final ResourceKey<Level> dimension;

        private Seen(ServerPlayer player) {
            this.instance = new WeakReference<>(player);
            this.dimension = player.level().dimension();
        }

        private boolean matches(ServerPlayer player) {
            return this.instance.get() == player && this.dimension.equals(player.level().dimension());
        }
    }

    private static final Map<UUID, Seen> SEEN = new HashMap<>();

    private static int resyncCountdown;

    /**
     * (Re-)schedules the heartbeat. Called from the command registrar, which core replays every
     * time a server builds its dispatcher - start-up and {@code /reload}. The scheduler queue is
     * wiped on server stop, so this is what puts it back for the next world.
     */
    public static void armHeartbeat() {
        TickScheduler.cancelAll(HEARTBEAT_TAG);
        SEEN.clear();
        // Perks hold per-player counters that nothing else clears on a world change.
        StagePerks.clearAll();
        resyncCountdown = 0;
        TickScheduler.runRepeating(StagePerks.TICK_PERIOD, -1, EvolveServerHooks::heartbeat)
                .tag(HEARTBEAT_TAG);
    }

    /** One pass over the online players. Runs every {@link StagePerks#TICK_PERIOD} ticks. */
    public static void heartbeat() {
        if (!EvolveFeature.isReady()) {
            return;
        }
        MinecraftServer server = TickScheduler.server();
        if (server == null) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            SEEN.clear();
            StagePerks.retain(Set.of());
            return;
        }

        Set<UUID> online = new HashSet<>(players.size());
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            online.add(id);
            try {
                Seen known = SEEN.get(id);
                if (known == null || !known.matches(player)) {
                    SEEN.put(id, new Seen(player));
                    onPlayerAppeared(player);
                }
                StagePerks.tick(player, EvolveManager.data(player));
            } catch (Throwable failure) {
                // Never let one player's bad state spend the scheduler's three-strike budget.
                LOG.error("[evolve] heartbeat failed for {}", player.getGameProfile().getName(),
                        failure);
            }
        }
        SEEN.keySet().retainAll(online);
        StagePerks.retain(online);

        if (--resyncCountdown <= 0) {
            resyncCountdown = RESYNC_EVERY;
            for (ServerPlayer player : players) {
                try {
                    EvolveManager.sync(player);
                } catch (Throwable failure) {
                    LOG.error("[evolve] roster re-sync failed for {}",
                            player.getGameProfile().getName(), failure);
                }
            }
        }
    }

    /**
     * A {@code ServerPlayer} we have not handled before in this dimension: a join, a respawn or a
     * portal. Vanilla does not copy attribute modifiers across a death, so this is where the body
     * is put back on.
     */
    private static void onPlayerAppeared(ServerPlayer player) {
        EvolutionData state = EvolveManager.data(player);
        StagePerks.revokeAll(player);

        if (state.transforming()) {
            long now = player.level().getGameTime();
            long remaining = state.transformEndTick() - now;
            if (remaining <= 0L) {
                // The sequence ran out while they were away - land it now, without the fx.
                Transformation.finish(player);
            } else {
                Transformation.resume(player, (int) Math.min(Integer.MAX_VALUE, remaining));
            }
        } else {
            EvolveManager.reapply(player);
        }

        EvolveManager.sendRosterTo(player);
        EvolveManager.sync(player);
    }

    /**
     * Called by the loader glue ({@code EntityTrackingEvents.START_TRACKING} on Fabric,
     * {@code PlayerEvent.StartTracking} on NeoForge) the moment {@code tracker} comes into range of
     * {@code tracked}.
     *
     * <p>Without this, a second player walking up to an Apex player saw a normal-sized player with
     * their own skin until the next five-second roster pass - which is exactly beat 5 of the clip.
     * One payload, a couple of dozen bytes, sent once per tracking start.
     */
    public static void onStartTracking(ServerPlayer tracker, Entity tracked) {
        if (!EvolveFeature.isReady()) {
            return;
        }
        if (tracked instanceof ServerPlayer target && target != tracker) {
            EvolveManager.syncTo(tracker, target);
        }
    }

    /**
     * Called from {@code EvolvePlayerMixin} when a player takes a fall. Drives the Titan stomp.
     */
    public static void onPlayerLanded(Player player, float fallDistance) {
        if (!EvolveFeature.isReady()) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel)) {
            return;
        }
        StagePerks.onLand(serverPlayer, EvolveManager.data(serverPlayer), fallDistance);
    }

    /**
     * Called from {@code EvolvePlayerMixin} at the head of {@code Player#attack}, before vanilla
     * reads {@code Attributes.ATTACK_DAMAGE}. Drives the Brute charge.
     */
    public static void onPlayerAttack(Player player, Entity target) {
        if (!EvolveFeature.isReady()) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel)) {
            return;
        }
        StagePerks.onAttack(serverPlayer, EvolveManager.data(serverPlayer), target);
    }

    /** Test hook: forget every remembered player so the next heartbeat re-applies everything. */
    public static void forgetAll() {
        SEEN.clear();
        resyncCountdown = 0;
    }

    private EvolveServerHooks() {
    }
}
