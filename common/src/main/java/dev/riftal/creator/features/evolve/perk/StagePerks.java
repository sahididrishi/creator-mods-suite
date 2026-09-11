package dev.riftal.creator.features.evolve.perk;

import dev.riftal.creator.features.evolve.data.EvolutionData;
import dev.riftal.creator.features.evolve.perk.impl.BurrowPerk;
import dev.riftal.creator.features.evolve.perk.impl.ChargePerk;
import dev.riftal.creator.features.evolve.perk.impl.RoarPerk;
import dev.riftal.creator.features.evolve.perk.impl.StompPerk;
import dev.riftal.creator.features.evolve.perk.impl.ThickSkinPerk;
import dev.riftal.creator.features.evolve.stage.EvolutionStage;
import dev.riftal.creator.features.evolve.stage.Stages;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The five built-in passives, one per stage, and the plumbing that runs them.
 *
 * <p>Perks are singletons and stateless apart from the odd per-player counter. They are driven from
 * {@code EvolveServerHooks}: {@link #tick} from the five-tick heartbeat, {@link #onLand} from the
 * fall-damage mixin, {@link #roar} from {@code /evolve roar}.
 */
public final class StagePerks {

    /** Ticks between {@link #tick} calls. Perks that count up use this as their step. */
    public static final int TICK_PERIOD = 5;

    private static final BurrowPerk BURROW = new BurrowPerk();
    private static final ThickSkinPerk THICK_SKIN = new ThickSkinPerk();
    private static final ChargePerk CHARGE = new ChargePerk();
    private static final StompPerk STOMP = new StompPerk();
    private static final RoarPerk ROAR = new RoarPerk();

    private static final List<StagePerk> ALL = List.of(BURROW, THICK_SKIN, CHARGE, STOMP, ROAR);

    private static final Map<String, StagePerk> BY_KEY = byKey();

    /** The perk attached to a stage ordinal, never null. */
    public static StagePerk forStage(int stageOrdinal) {
        EvolutionStage stage = Stages.byOrdinal(stageOrdinal);
        StagePerk perk = BY_KEY.get(stage.perkKey());
        return perk == null ? BURROW : perk;
    }

    /** Every perk, ladder order. */
    public static List<StagePerk> all() {
        return ALL;
    }

    /** Runs the stage's passive. Called every {@link #TICK_PERIOD} ticks for every online player. */
    public static void tick(ServerPlayer player, EvolutionData data) {
        if (data.transforming()) {
            return;
        }
        forStage(data.stage()).tick(player);
    }

    /**
     * Runs the stage's landing hook.
     *
     * @return true when the perk did something
     */
    public static boolean onLand(ServerPlayer player, EvolutionData data, float fallDistance) {
        if (data.transforming()) {
            return false;
        }
        return forStage(data.stage()).onLand(player, fallDistance);
    }

    /**
     * Runs the stage's roar hook.
     *
     * @return how many mobs were affected; 0 for every stage but Apex
     */
    public static int roar(ServerPlayer player, EvolutionData data) {
        return forStage(data.stage()).onRoar(player);
    }

    /** Drops every perk's per-player bookkeeping for this player. */
    public static void revokeAll(ServerPlayer player) {
        for (StagePerk perk : ALL) {
            perk.revoke(player);
        }
    }

    private static Map<String, StagePerk> byKey() {
        Map<String, StagePerk> map = new LinkedHashMap<>();
        for (StagePerk perk : ALL) {
            map.put(perk.key(), perk);
        }
        return Map.copyOf(map);
    }

    private StagePerks() {
    }
}
