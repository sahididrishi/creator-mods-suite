package dev.riftal.creator.features.colossus;

import dev.riftal.creator.core.sched.TickScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The burning floor the ash bombs leave behind.
 *
 * <p>Patches are temporary on purpose: a boss fight that permanently sets the arena alight is
 * unusable for a second take. Every placed fire block is remembered and cleaned up by a
 * {@link TickScheduler} task tagged {@link #OWNER}, so {@code /colossus kill} can wipe them all.
 */
public final class FirePatches {

    /** Scheduler tag every patch-removal task carries. */
    public static final ResourceLocation OWNER =
            ResourceLocation.fromNamespaceAndPath(ColossusFeature.NAMESPACE, "fire_patch");

    /** How long a patch burns before it is removed again. */
    public static final int DEFAULT_LIFETIME_TICKS = 80;

    /**
     * Lights a square patch of floor around {@code centre}.
     *
     * @param radius        half-width in blocks; 1 gives the 3x3 the ash bomb uses
     * @param lifetimeTicks ticks before the fire is taken away again
     * @return how many fire blocks were actually placed
     */
    public static int scatter(ServerLevel level, BlockPos centre, int radius, int lifetimeTicks) {
        List<BlockPos> placed = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos surface = findSurface(level, centre.offset(dx, 0, dz));
                if (surface != null) {
                    BlockState fire = BaseFireBlock.getState(level, surface);
                    level.setBlockAndUpdate(surface, fire);
                    placed.add(surface.immutable());
                }
            }
        }
        if (!placed.isEmpty()) {
            TickScheduler.runLater(Math.max(1, lifetimeTicks), () -> extinguish(level, placed))
                    .tag(OWNER);
        }
        return placed.size();
    }

    /** Removes any fire block still standing at the remembered positions. */
    public static void extinguish(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock) {
                level.removeBlock(pos, false);
            }
        }
    }

    /** Cancels every pending patch cleanup. Called when the fight ends. */
    public static void cancelPending() {
        TickScheduler.cancelAll(OWNER);
    }

    private static BlockPos findSurface(ServerLevel level, BlockPos around) {
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos candidate = around.offset(0, dy, 0);
            if (BaseFireBlock.canBePlacedAt(level, candidate, Direction.UP)) {
                return candidate;
            }
        }
        return null;
    }

    private FirePatches() {
    }
}
