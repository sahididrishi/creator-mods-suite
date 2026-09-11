package dev.riftal.creator.features.evolve.progression;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.data.EvolutionData;
import dev.riftal.creator.features.evolve.net.SyncEvolutionPayload;
import dev.riftal.creator.features.evolve.net.XpPopupPayload;
import dev.riftal.creator.features.evolve.perk.StagePerks;
import dev.riftal.creator.features.evolve.stage.StageMath;
import dev.riftal.creator.features.evolve.stage.StageModifiers;
import dev.riftal.creator.features.evolve.stage.Stages;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The server-side brain. Everything that changes a player's stage or XP goes through here so that
 * the attachment, the attribute modifiers and the clients never drift apart.
 *
 * <p>All methods are no-ops when the feature is switched off in {@code config/creatormods.json}.
 */
public final class EvolveManager {

    /** Largest XP delta a single command may apply, in either direction. */
    public static final int MAX_COMMAND_XP = 1_000_000;

    /** Current state for any entity, or {@link EvolutionData#INITIAL} when the feature is off. */
    public static EvolutionData data(Entity holder) {
        if (!EvolveFeature.isReady()) {
            return EvolutionData.INITIAL;
        }
        return EvolveFeature.data().get(holder);
    }

    /** Overwrites the state. Does not touch attributes or the client - callers do that. */
    public static void setData(Entity holder, EvolutionData value) {
        if (EvolveFeature.isReady()) {
            EvolveFeature.data().set(holder, value);
        }
    }

    /**
     * Adds (or, with a negative amount, removes) evolution XP and starts a transformation if the
     * player crossed a threshold. A multi-stage jump runs a single transformation straight to the
     * final stage.
     *
     * @param source one of the {@code XpPopupPayload.SOURCE_*} constants
     * @return the stage the player will end up on
     */
    public static int addXp(ServerPlayer player, int amount, int source) {
        if (!EvolveFeature.isReady() || amount == 0) {
            return data(player).stage();
        }
        int clamped = Math.max(-MAX_COMMAND_XP, Math.min(MAX_COMMAND_XP, amount));

        EvolutionData before = data(player);
        int newXp = Math.max(0, before.xp() + clamped);
        EvolutionData after = before.withXp(newXp);
        setData(player, after);

        if (clamped > 0) {
            Payloads.sendToPlayer(player, new XpPopupPayload(clamped, source));
        }

        int target = StageMath.stageForXp(newXp);
        if (target > after.stage() && !after.transforming()) {
            Transformation.begin(player, target);
        } else if (target < after.stage()) {
            // XP was taken away below the current stage's floor: step down with no ceremony, and
            // keep the XP the player is actually holding rather than pinning it to the new stage's
            // floor - /evolve xp -250 from 500 must leave 250, not 100.
            setData(player, after.withStage(target));
            applyBody(player, target);
        } else {
            sync(player);
        }
        return target;
    }

    /** Bumps the lifetime kill counter. */
    public static void recordKill(ServerPlayer player) {
        if (!EvolveFeature.isReady()) {
            return;
        }
        EvolutionData current = data(player);
        setData(player, current.withKills(current.totalKills() + 1));
    }

    /**
     * Moves a player to an exact stage. XP is pinned to that stage's floor so the HUD bar and any
     * later {@code /evolve xp} stay consistent with the stage.
     *
     * @param instant skip the transformation sequence and snap straight to the new body
     */
    public static void setStage(ServerPlayer player, int stage, boolean instant) {
        if (!EvolveFeature.isReady()) {
            return;
        }
        int target = Stages.clamp(stage);
        EvolutionData current = data(player);

        // A stage change always ends any sequence that was already running.
        Transformation.abort(player);
        setData(player, current.atStage(target).withTransform(false, 0L));

        if (instant || target <= current.stage()) {
            applyStageNow(player, target);
        } else {
            Transformation.begin(player, target);
        }
    }

    /**
     * Writes the modifiers for {@code stage} immediately, swaps the perk bookkeeping and syncs.
     * Used by {@code instant} commands, by step-downs, and by the join/respawn re-apply.
     */
    public static void applyStageNow(ServerPlayer player, int stage) {
        if (!EvolveFeature.isReady()) {
            return;
        }
        int target = Stages.clamp(stage);
        EvolutionData current = data(player);
        if (current.stage() != target) {
            setData(player, current.atStage(target));
        }
        applyBody(player, target);
    }

    /**
     * Writes the modifiers for {@code stage} and syncs, without touching the stored stage or XP.
     * The caller owns the attachment; this only puts the body on.
     */
    public static void applyBody(ServerPlayer player, int stage) {
        if (!EvolveFeature.isReady()) {
            return;
        }
        StagePerks.revokeAll(player);
        StageModifiers.unlockMovement(player);
        StageModifiers.apply(player, Stages.byOrdinal(Stages.clamp(stage)));
        sync(player);
    }

    /** Re-writes the modifiers for whatever stage the player is already on. Idempotent. */
    public static void reapply(ServerPlayer player) {
        if (!EvolveFeature.isReady()) {
            return;
        }
        StageModifiers.apply(player, Stages.byOrdinal(data(player).stage()));
        sync(player);
    }

    /** Back to Hatchling with no XP, no perk state, no model override and no running sequence. */
    public static void reset(ServerPlayer player) {
        if (!EvolveFeature.isReady()) {
            return;
        }
        Transformation.abort(player);
        StagePerks.revokeAll(player);
        StageModifiers.unlockMovement(player);
        StageModifiers.clear(player);
        setData(player, EvolutionData.INITIAL);
        StageModifiers.apply(player, Stages.HATCHLING);
        player.setHealth(player.getMaxHealth());
        sync(player);
    }

    /** Forces, clears or releases the beast model swap. */
    public static void setModelOverride(ServerPlayer player, int override) {
        if (!EvolveFeature.isReady()) {
            return;
        }
        setData(player, data(player).withModelOverride(override));
        sync(player);
    }

    /** The sync packet describing one player's current state. */
    public static SyncEvolutionPayload payloadFor(ServerPlayer player) {
        EvolutionData state = data(player);
        return new SyncEvolutionPayload(player.getUUID(), state.stage(), state.xp(),
                state.transforming(), state.modelOverride());
    }

    /** Sends one player's state to that player and to everyone tracking them. */
    public static void sync(ServerPlayer player) {
        if (!EvolveFeature.isReady()) {
            return;
        }
        Payloads.sendToTracking(player, payloadFor(player));
    }

    /** Sends every online player's state to one viewer. Used when that viewer joins or respawns. */
    public static void sendRosterTo(ServerPlayer viewer) {
        if (!EvolveFeature.isReady()) {
            return;
        }
        MinecraftServer server = viewer.getServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            Payloads.sendToPlayer(viewer, payloadFor(other));
        }
    }

    private EvolveManager() {
    }
}
