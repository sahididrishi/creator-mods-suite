package dev.riftal.creator.features.events.block;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.events.EventManager;
import dev.riftal.creator.features.events.EventsFeature;
import dev.riftal.creator.features.events.api.WorldEvent;
import dev.riftal.creator.features.events.events.LuckyRainEvent;
import dev.riftal.creator.features.events.lucky.LuckyOutcome;
import dev.riftal.creator.features.events.lucky.LuckyOutcomeRunner;
import dev.riftal.creator.features.events.lucky.LuckyOutcomes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The gold "?" block that rains down during {@code luckyrain}.
 *
 * <p>Spawned as a {@link FallingBlockEntity} with {@code disableDrop()}, so vanilla never re-places
 * it and always routes through {@link #onBrokenAfterFall}; {@link #onLand} is implemented as well
 * for the case where something else drops the block normally.
 */
public class LuckyRainBlock extends Block implements Fallable {

    public LuckyRainBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void onLand(Level level, BlockPos pos, BlockState state, BlockState replaceableState,
                       FallingBlockEntity fallingBlock) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.removeBlock(pos, false);
            roll(serverLevel, pos);
        }
    }

    @Override
    public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity fallingBlock) {
        if (level instanceof ServerLevel serverLevel) {
            roll(serverLevel, pos);
        }
    }

    /**
     * Picks one outcome from the active drop table and runs it. Safe to call from anywhere on the
     * server: a broken table or a bad outcome is logged, never thrown.
     */
    public static void roll(ServerLevel level, BlockPos pos) {
        List<LuckyOutcome> outcomes = EventsFeature.luckyOutcomes(level.getServer());
        RandomSource random = level.getRandom();
        LuckyOutcome outcome = LuckyOutcomes.pick(outcomes, activeLuck(), random.nextDouble());
        if (outcome == null) {
            return;
        }
        Fx.sound(level, Vec3.atCenterOf(pos), EventsFeature.luckyPop(), SoundSource.BLOCKS, 1.0F,
                0.9F + random.nextFloat() * 0.3F);
        try {
            if (!LuckyOutcomeRunner.run(level, pos, outcome, random)) {
                LOG.debug("[events] lucky outcome '{}' did nothing at {}", outcome.type(), pos);
            }
        } catch (RuntimeException e) {
            LOG.error("[events] lucky outcome '{}' failed", outcome.type(), e);
        }
    }

    /**
     * The {@code luck=} the running {@code luckyrain} was started with, or 0 when the block was
     * dropped by something else entirely. Read here rather than passed in because {@code Fallable}
     * gives the block no way to know which event spawned it.
     */
    private static int activeLuck() {
        WorldEvent active = EventManager.active();
        return active instanceof LuckyRainEvent rain ? rain.luck() : 0;
    }
}
