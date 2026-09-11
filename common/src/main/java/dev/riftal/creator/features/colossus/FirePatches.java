package dev.riftal.creator.features.colossus;

import dev.riftal.creator.core.sched.ScheduledTask;
import dev.riftal.creator.core.sched.TickScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * The burning floor the ash bombs leave behind.
 *
 * <p>Patches are temporary on purpose: a boss fight that permanently sets the arena alight is
 * unusable for a second take. Every placed fire block is remembered here, in a list this class
 * owns, and a {@link TickScheduler} task tagged {@link #OWNER} takes it away again once the patch
 * has burned its lifetime out.
 *
 * <p><b>The positions are owned by this class, not by the scheduler.</b> Cancelling the scheduled
 * task cancels the <em>removal</em>, which is the exact opposite of putting the fire out - so
 * {@code /colossus kill} calls {@link #extinguishAll(ServerLevel)}, which runs every outstanding
 * cleanup immediately and then drops its task. Nothing in this feature may cancel {@link #OWNER}
 * tasks directly.
 */
public final class FirePatches {

    /** Scheduler tag every patch-removal task carries. */
    public static final ResourceLocation OWNER =
            ResourceLocation.fromNamespaceAndPath(ColossusFeature.NAMESPACE, "fire_patch");

    /** How long a patch burns before it is removed again. */
    public static final int DEFAULT_LIFETIME_TICKS = 80;

    /** Patches that are still alight, oldest first. Server thread only. */
    private static final List<Patch> PENDING = new ArrayList<>();

    /** One lit patch and the task that will put it out. */
    private static final class Patch {

        private final ServerLevel level;
        private final List<BlockPos> positions;
        @Nullable
        private ScheduledTask task;

        private Patch(ServerLevel level, List<BlockPos> positions) {
            this.level = level;
            this.positions = positions;
        }
    }

    /**
     * Lights a square patch of floor around {@code centre}.
     *
     * @param radius        half-width in blocks; 1 gives the 3x3 the ash bomb uses
     * @param lifetimeTicks ticks before the fire is taken away again
     * @return how many fire blocks were actually placed
     */
    public static int scatter(ServerLevel level, BlockPos centre, int radius, int lifetimeTicks) {
        forgetOtherServers(level);

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
            Patch patch = new Patch(level, placed);
            PENDING.add(patch);
            patch.task = TickScheduler.runLater(Math.max(1, lifetimeTicks), () -> {
                PENDING.remove(patch);
                extinguish(patch.level, patch.positions);
            }).tag(OWNER);
        }
        return placed.size();
    }

    /**
     * Puts out every patch still burning in {@code level} right now and drops its pending cleanup.
     * Called when the fight ends, so take two starts on a floor that is not already on fire.
     *
     * @return how many fire blocks were removed
     */
    public static int extinguishAll(ServerLevel level) {
        int removed = 0;
        for (Patch patch : new ArrayList<>(PENDING)) {
            if (patch.level != level) {
                continue;
            }
            PENDING.remove(patch);
            if (patch.task != null) {
                patch.task.cancel();
            }
            removed += extinguish(patch.level, patch.positions);
        }
        return removed;
    }

    /** Patches still waiting to burn out. Diagnostics and tests. */
    public static int pendingPatches() {
        return PENDING.size();
    }

    /**
     * Removes any fire block still standing at the remembered positions.
     *
     * @return how many blocks were actually removed
     */
    public static int extinguish(ServerLevel level, List<BlockPos> positions) {
        int removed = 0;
        for (BlockPos pos : positions) {
            if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock) {
                level.removeBlock(pos, false);
                removed++;
            }
        }
        return removed;
    }

    /**
     * Drops patches belonging to a server that is no longer running. {@code TickScheduler} is
     * cleared on server stop without running anything, so without this the list would keep a dead
     * {@code ServerLevel} alive across a single-player world swap.
     */
    private static void forgetOtherServers(ServerLevel current) {
        PENDING.removeIf(patch -> patch.level.getServer() != current.getServer());
    }

    @Nullable
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
