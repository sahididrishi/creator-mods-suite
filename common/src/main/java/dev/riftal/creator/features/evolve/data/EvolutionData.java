package dev.riftal.creator.features.evolve.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.riftal.creator.features.evolve.stage.StageMath;
import dev.riftal.creator.features.evolve.stage.Stages;

/**
 * Everything Evolve remembers about one player. Stored through
 * {@code dev.riftal.creator.core.data.PlayerData}, which requires the value to be immutable - hence
 * a record with {@code withX} copies rather than setters.
 *
 * <p>On disk it looks like
 * {@code {"stage":3,"xp":412,"total_kills":27,"transforming":false,"transform_end_tick":0,
 * "model_override":0}}. Every field is optional so old saves and hand-edited NBT still load.
 *
 * @param modelOverride -1 forces the player skin, 0 follows the stage, 1 forces the Apex beast
 */
public record EvolutionData(int stage, int xp, int totalKills, boolean transforming,
                            long transformEndTick, int modelOverride) {

    /** Model override: always render the player's own skin. */
    public static final int MODEL_FORCED_OFF = -1;

    /** Model override: render whatever the current stage asks for. */
    public static final int MODEL_AUTO = 0;

    /** Model override: always render the Apex beast. */
    public static final int MODEL_FORCED_ON = 1;

    /** A brand new player: Hatchling, no XP. */
    public static final EvolutionData INITIAL =
            new EvolutionData(Stages.MIN, 0, 0, false, 0L, MODEL_AUTO);

    public static final Codec<EvolutionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(Stages.MIN, Stages.MAX).optionalFieldOf("stage", Stages.MIN)
                    .forGetter(EvolutionData::stage),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(EvolutionData::xp),
            Codec.INT.optionalFieldOf("total_kills", 0).forGetter(EvolutionData::totalKills),
            Codec.BOOL.optionalFieldOf("transforming", false).forGetter(EvolutionData::transforming),
            Codec.LONG.optionalFieldOf("transform_end_tick", 0L).forGetter(EvolutionData::transformEndTick),
            Codec.intRange(MODEL_FORCED_OFF, MODEL_FORCED_ON).optionalFieldOf("model_override", MODEL_AUTO)
                    .forGetter(EvolutionData::modelOverride)
    ).apply(instance, EvolutionData::new));

    public EvolutionData {
        stage = Stages.clamp(stage);
        xp = Math.max(0, xp);
        totalKills = Math.max(0, totalKills);
        transformEndTick = Math.max(0L, transformEndTick);
        modelOverride = modelOverride < MODEL_FORCED_OFF ? MODEL_FORCED_OFF
                : Math.min(modelOverride, MODEL_FORCED_ON);
    }

    /** A fresh instance for players who have never evolved. */
    public static EvolutionData initial() {
        return INITIAL;
    }

    public EvolutionData withStage(int newStage) {
        return new EvolutionData(newStage, xp, totalKills, transforming, transformEndTick, modelOverride);
    }

    public EvolutionData withXp(int newXp) {
        return new EvolutionData(stage, newXp, totalKills, transforming, transformEndTick, modelOverride);
    }

    /** Sets the stage and pins XP to that stage's floor - used by {@code /evolve set}. */
    public EvolutionData atStage(int newStage) {
        int clamped = Stages.clamp(newStage);
        return new EvolutionData(clamped, StageMath.xpFloorForStage(clamped), totalKills,
                transforming, transformEndTick, modelOverride);
    }

    public EvolutionData withKills(int newTotalKills) {
        return new EvolutionData(stage, xp, newTotalKills, transforming, transformEndTick, modelOverride);
    }

    public EvolutionData withTransform(boolean nowTransforming, long endTick) {
        return new EvolutionData(stage, xp, totalKills, nowTransforming, endTick, modelOverride);
    }

    public EvolutionData withModelOverride(int newOverride) {
        return new EvolutionData(stage, xp, totalKills, transforming, transformEndTick, newOverride);
    }

    /** Progress towards the next stage, 0..1. */
    public float progress() {
        return StageMath.progressFraction(xp, stage);
    }

    /** XP still needed for the next transformation, 0 at Apex. */
    public int xpToNext() {
        return StageMath.xpToNextStage(xp, stage);
    }

    /** True when this player should be drawn as the Apex beast. */
    public boolean usesBeastModel() {
        if (modelOverride == MODEL_FORCED_ON) {
            return true;
        }
        if (modelOverride == MODEL_FORCED_OFF) {
            return false;
        }
        return Stages.byOrdinal(stage).beastModel();
    }
}
