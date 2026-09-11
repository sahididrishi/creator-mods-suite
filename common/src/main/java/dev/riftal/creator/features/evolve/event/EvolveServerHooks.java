package dev.riftal.creator.features.evolve.event;

import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.data.EvolutionData;
import dev.riftal.creator.features.evolve.perk.StagePerks;
import dev.riftal.creator.features.evolve.progression.EvolveManager;
import dev.riftal.creator.features.evolve.progression.Transformation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Evolve's server-side heartbeat, and the entry points the mixins call into.
 *
 * <p>Core exposes no join / respawn / dimension-change events, so instead of three loader-specific
 * hooks there is one five-tick task that walks the (tiny) online player list. A player whose
 * {@code ServerPlayer} instance is new - first join, respawn after death, or a relog - is spotted
 * by their entity id changing, and gets their modifiers re-applied, any interrupted transformation
 * resolved, and the whole roster re-sent so their HUD and the beast model are correct immediately.
 *
 * <p>The same pass runs the stage passives. A full roster re-sync goes out every five seconds so a
 * client that walked into tracking range late still learns everyone's stage; the payload is a
 * couple of dozen bytes per player, which is nothing next to a single entity position update.
 */
public final class EvolveServerHooks {

    /** Scheduler tag for the heartbeat, so it can be cancelled and re-armed on its own. */
    public static final ResourceLocation HEARTBEAT_TAG = EvolveFeature.id("heartbeat");

    /** Heartbeats between full roster re-syncs. 20 heartbeats x 5 ticks = 5 seconds. */
    private static final int RESYNC_EVERY = 20;

    /** Player uuid to the entity id of the {@code ServerPlayer} instance we last handled. */
    private static final Map<UUID, Integer> SEEN = new HashMap<>();

    private static int resyncCountdown;

    /**
     * (Re-)schedules the heartbeat. Called from the command registrar, which core replays every
     * time a server builds its dispatcher - start-up and {@code /reload}. The scheduler queue is
     * wiped on server stop, so this is what puts it back for the next world.
     */
    public static void armHeartbeat() {
        TickScheduler.cancelAll(HEARTBEAT_TAG);
        SEEN.clear();
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
            return;
        }

        Set<UUID> online = new HashSet<>(players.size());
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            online.add(id);
            Integer known = SEEN.get(id);
            if (known == null || known.intValue() != player.getId()) {
                SEEN.put(id, player.getId());
                onPlayerAppeared(player);
            }
            StagePerks.tick(player, EvolveManager.data(player));
        }
        SEEN.keySet().retainAll(online);

        if (--resyncCountdown <= 0) {
            resyncCountdown = RESYNC_EVERY;
            for (ServerPlayer player : players) {
                EvolveManager.sync(player);
            }
        }
    }

    /**
     * A {@code ServerPlayer} we have not handled before: a join, a respawn or a dimension move that
     * rebuilt the entity. Vanilla does not copy attribute modifiers across a death, so this is
     * where the body is put back on.
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

    /** Test hook: forget every remembered player so the next heartbeat re-applies everything. */
    public static void forgetAll() {
        SEEN.clear();
        resyncCountdown = 0;
    }

    private EvolveServerHooks() {
    }
}
