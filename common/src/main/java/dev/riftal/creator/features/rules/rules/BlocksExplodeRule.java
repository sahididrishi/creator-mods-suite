package dev.riftal.creator.features.rules.rules;

import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.core.util.CooldownTracker;
import dev.riftal.creator.features.rules.RuleIds;
import dev.riftal.creator.features.rules.api.Rule;
import dev.riftal.creator.features.rules.api.RuleContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * <b>Minecraft but every block you mine explodes.</b>
 *
 * <p>Radius 2, {@link Level.ExplosionInteraction#NONE} - it hurts and it looks great, but it does
 * not eat the build behind the creator, which is the difference between a usable clip and a
 * re-shoot.
 *
 * <p>The explosion is queued on the tick scheduler rather than fired inline: the break is still
 * finishing when the hook runs, and an explosion inside that window can drop the block twice.
 */
public final class BlocksExplodeRule implements Rule {

    /** Explosion radius in blocks. */
    public static final float RADIUS = 2.0F;

    /** Minimum ticks between two explosions caused by the same player. */
    public static final int COOLDOWN_TICKS = 5;

    private final CooldownTracker<UUID> cooldowns = new CooldownTracker<>();

    @Override
    public String id() {
        return "blocks_explode";
    }

    @Override
    public void onEnable(RuleContext ctx) {
        cooldowns.clearAll();
    }

    @Override
    public void onDisable(RuleContext ctx) {
        cooldowns.clearAll();
        TickScheduler.cancelAll(RuleIds.SCHED_EXPLODE);
    }

    @Override
    public void onBlockBroken(RuleContext ctx, ServerPlayer player, ServerLevel level, BlockPos pos,
                              BlockState state) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        long now = level.getGameTime();
        if (!cooldowns.ready(player.getUUID(), now)) {
            return;
        }
        cooldowns.start(player.getUUID(), now, COOLDOWN_TICKS);

        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        TickScheduler.runLater(1,
                        () -> level.explode(null, x, y, z, RADIUS, false, Level.ExplosionInteraction.NONE))
                .tag(RuleIds.SCHED_EXPLODE);
    }
}
