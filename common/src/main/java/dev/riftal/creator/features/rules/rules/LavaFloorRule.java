package dev.riftal.creator.features.rules.rules;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.rules.RuleTags;
import dev.riftal.creator.features.rules.api.Rule;
import dev.riftal.creator.features.rules.api.RuleContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>Minecraft but the floor turns to lava if you stand still.</b>
 *
 * <p>Two seconds on the same block turns the block under the player's feet into a lava source and
 * resets the counter, so a creator who keeps moving leaves a glowing trail behind them and one who
 * stops burns.
 *
 * <p>Creative and spectator players are ignored, and blocks in
 * {@code #creator_rules:lava_floor_immune} plus anything unbreakable (bedrock, barriers) survive -
 * a rule that eats the set it is filmed on is not funny twice.
 */
public final class LavaFloorRule implements Rule {

    /** Ticks a player must hold the same block position before the floor goes. */
    public static final int DELAY_TICKS = 40;

    private final Map<UUID, BlockPos> lastPos = new HashMap<>();
    private final Map<UUID, Integer> standing = new HashMap<>();

    @Override
    public String id() {
        return "lava_floor";
    }

    @Override
    public void onEnable(RuleContext ctx) {
        lastPos.clear();
        standing.clear();
    }

    @Override
    public void onDisable(RuleContext ctx) {
        lastPos.clear();
        standing.clear();
    }

    @Override
    public void onPlayerRespawn(RuleContext ctx, ServerPlayer player) {
        forget(player.getUUID());
    }

    @Override
    public void tick(RuleContext ctx) {
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer player : ctx.players()) {
            UUID uuid = player.getUUID();
            seen.add(uuid);
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
                forget(uuid);
                continue;
            }
            BlockPos pos = player.blockPosition();
            BlockPos previous = lastPos.get(uuid);
            if (!pos.equals(previous) || !player.onGround()) {
                lastPos.put(uuid, pos);
                standing.put(uuid, 0);
                continue;
            }
            int ticks = standing.getOrDefault(uuid, 0) + 1;
            if (ticks < DELAY_TICKS) {
                standing.put(uuid, ticks);
                continue;
            }
            standing.put(uuid, 0);
            melt(player.serverLevel(), pos.below());
        }
        if (standing.size() > seen.size()) {
            standing.keySet().retainAll(seen);
            lastPos.keySet().retainAll(seen);
        }
    }

    private void forget(UUID uuid) {
        standing.remove(uuid);
        lastPos.remove(uuid);
    }

    /**
     * Turns one block into a lava source, unless it is air, already a fluid, tagged
     * {@code #creator_rules:lava_floor_immune} or unbreakable. Public and static so the GameTest can
     * drive exactly this decision without having to stand a player still for two seconds.
     *
     * @return true when the block was actually replaced
     */
    public static boolean melt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.is(RuleTags.LAVA_FLOOR_IMMUNE)) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
        Fx.sound(level, Vec3.atCenterOf(pos), SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.8F, 1.0F);
        return true;
    }
}
