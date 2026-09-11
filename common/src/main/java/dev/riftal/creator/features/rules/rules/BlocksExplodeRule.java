package dev.riftal.creator.features.rules.rules;

import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.core.util.CooldownTracker;
import dev.riftal.creator.features.rules.RuleIds;
import dev.riftal.creator.features.rules.api.Rule;
import dev.riftal.creator.features.rules.api.RuleContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
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
 *
 * <p><b>The explosion spares dropped items.</b> {@code ExplosionInteraction.NONE} only stops the
 * blast from breaking <em>blocks</em>; vanilla still hurts every entity in twice the radius, and an
 * {@link ItemEntity} has 5 health against roughly 24 damage at point blank. The drop the player
 * just mined sits half a block from the blast origin, so without the damage calculator below,
 * {@code blocks_explode} silently turned mining into a no-drop action - and {@code chaos.json}
 * contains both this rule and {@code random_drops}, whose whole point is the item that comes out.
 */
public final class BlocksExplodeRule implements Rule {

    /** Explosion radius in blocks. */
    public static final float RADIUS = 2.0F;

    /** Minimum ticks between two explosions caused by the same player. */
    public static final int COOLDOWN_TICKS = 5;

    /**
     * Vanilla's damage numbers for everything that can fight back, and no damage at all for loose
     * items and experience. Knockback is untouched, so the drops still get thrown about - they just
     * survive to be picked up.
     */
    private static final ExplosionDamageCalculator SPARE_DROPS = new ExplosionDamageCalculator() {
        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return !(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb);
        }
    };

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
        trigger(level, player.getUUID(), pos);
    }

    /**
     * Rate-limits one player and schedules the blast for the next tick.
     *
     * <p>Public and typed on a plain {@link UUID} so the GameTest can drive the real cooldown path
     * without standing up a {@code ServerPlayer}.
     *
     * @return true when an explosion was scheduled, false when the cooldown swallowed it
     */
    public boolean trigger(ServerLevel level, UUID playerId, BlockPos pos) {
        long now = level.getGameTime();
        if (!cooldowns.ready(playerId, now)) {
            return false;
        }
        cooldowns.start(playerId, now, COOLDOWN_TICKS);

        BlockPos frozen = pos.immutable();
        TickScheduler.runLater(1, () -> detonate(level, frozen)).tag(RuleIds.SCHED_EXPLODE);
        return true;
    }

    /**
     * The blast itself: no block damage, no fire, and no damage to the items the break just popped.
     * Public and static so the GameTest can assert exactly this behaviour.
     */
    public static void detonate(ServerLevel level, BlockPos pos) {
        level.explode(null, null, SPARE_DROPS,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                RADIUS, false, Level.ExplosionInteraction.NONE);
    }
}
