package dev.riftal.creator.features.colossus;

import dev.riftal.creator.core.util.MathUtil;
import dev.riftal.creator.core.util.Selection;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/**
 * The phase 3 wall of fire that closes in on the arena.
 *
 * <p>The radius shrinks linearly from the arena radius to {@link #MIN_RADIUS} and then stops;
 * anything caught outside it burns. {@link #radiusAt} and {@link #isOutside} are pure and
 * unit-tested, the rest is the server-side presentation.
 */
public final class ArenaRing {

    /** The ring never closes tighter than this, so the boss always has a floor to fight on. */
    public static final double MIN_RADIUS = 6.0D;

    /** How fast the ring closes, in blocks per second. 20 blocks -> 6 blocks takes 40 seconds. */
    public static final double BLOCKS_PER_SECOND = 0.35D;

    /** Particles per ring redraw. */
    public static final int RING_POINTS = 48;

    /** Fire damage dealt to anything outside the ring, every {@link #BURN_INTERVAL_TICKS}. */
    public static final float BURN_DAMAGE = 3.0F;

    /** How often outsiders are checked and burned. */
    public static final int BURN_INTERVAL_TICKS = 20;

    /** How often the ring is redrawn. */
    public static final int DRAW_INTERVAL_TICKS = 5;

    /** Ticks of burning applied to anything outside the ring. */
    public static final int BURN_TICKS = 60;

    /** Ring radius {@code ticksInPhase3} ticks after the enrage started. Clamped to the two ends. */
    public static double radiusAt(double startRadius, double minRadius, double blocksPerSecond,
                                  int ticksInPhase3) {
        double shrunk = startRadius - blocksPerSecond * (Math.max(0, ticksInPhase3) / 20.0D);
        return MathUtil.clamp(shrunk, Math.min(minRadius, startRadius), startRadius);
    }

    /** True when {@code pos} is outside the ring. Height is ignored: this is a wall, not a dome. */
    public static boolean isOutside(Vec3 centre, Vec3 pos, double radius) {
        return MathUtil.horizontalDistance(centre, pos) > radius;
    }

    /** Draws the burning circle. Server side, so every player in range sees it. */
    public static void draw(ServerLevel level, Vec3 centre, double radius) {
        Vec3[] points = MathUtil.ring(centre, radius, RING_POINTS);
        for (int i = 0; i < points.length; i++) {
            Vec3 point = points[i];
            level.sendParticles(ParticleTypes.FLAME, point.x, point.y + 0.2D, point.z,
                    1, 0.05D, 0.35D, 0.05D, 0.01D);
            if (i % 4 == 0) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y + 1.1D, point.z,
                        1, 0.05D, 0.4D, 0.05D, 0.01D);
            }
        }
    }

    /** Sets fire to everything standing outside the ring. */
    public static void burnOutsiders(ServerLevel level, LivingEntity source, Vec3 centre,
                                     double radius, double searchRadius,
                                     Predicate<LivingEntity> canBurn) {
        List<LivingEntity> nearby = Selection.around(level, LivingEntity.class, centre,
                searchRadius, e -> e != source && canBurn.test(e));
        for (LivingEntity victim : nearby) {
            if (!isOutside(centre, victim.position(), radius)) {
                continue;
            }
            victim.setRemainingFireTicks(BURN_TICKS);
            victim.hurt(level.damageSources().onFire(), BURN_DAMAGE);
        }
    }

    /** Convenience for callers that only have an {@link Entity}. */
    public static boolean isOutside(Vec3 centre, Entity entity, double radius) {
        return isOutside(centre, entity.position(), radius);
    }

    private ArenaRing() {
    }
}
