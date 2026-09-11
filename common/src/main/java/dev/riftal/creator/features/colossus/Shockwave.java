package dev.riftal.creator.features.colossus;

import dev.riftal.creator.core.util.MathUtil;
import dev.riftal.creator.core.util.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The expanding ground ring the slam throws out.
 *
 * <p>The maths half ({@link #radiusAt}, {@link #victims}, {@link #knockback}) is pure and
 * unit-tested; {@link #apply} is the server-side half that damages, knocks back and draws the ring.
 *
 * <p>Everything is measured on the horizontal plane: the ring races across the floor, so a player
 * standing on a two-block pillar inside it is still hit.
 */
public final class Shockwave {

    /** Vertical kick added to every knockback, so victims are thrown up as well as out. */
    public static final double KNOCKBACK_UP = 0.4D;

    /** How thick the travelling band is, in blocks. */
    public static final double BAND_WIDTH = 1.5D;

    /** Ticks the ring takes to reach {@code maxRadius}. */
    public static final int EXPANSION_TICKS = 8;

    /**
     * Points drawn around the ring each tick. Deliberately modest: the ring is redrawn on every one
     * of the {@link #EXPANSION_TICKS}, so this number is multiplied by eight for one slam.
     */
    public static final int RING_POINTS = 16;

    /**
     * Every n-th ring point carries the heavier effects (smoke, and on the impact tick the vanilla
     * block-break crack).
     */
    private static final int ACCENT_EVERY = 4;

    /** Radius of the leading edge, {@code tick} ticks into an {@code ticks}-tick expansion. */
    public static double radiusAt(int tick, int ticks, double maxRadius) {
        if (ticks <= 0) {
            return maxRadius;
        }
        double t = MathUtil.clamp((double) tick / (double) ticks, 0.0D, 1.0D);
        return maxRadius * t;
    }

    /**
     * True when {@code pos} lies in the travelling band {@code [inner, outer]} around
     * {@code centre}. Horizontal only, both ends inclusive.
     *
     * <p>Split out of {@link #victims} so the band itself can be unit-tested: a JUnit run has no
     * registries and therefore cannot construct a {@link LivingEntity} to feed the list version.
     */
    public static boolean isInBand(Vec3 centre, Vec3 pos, double inner, double outer) {
        double distance = MathUtil.horizontalDistance(centre, pos);
        return distance >= inner && distance <= outer;
    }

    /** Everything whose horizontal distance from {@code centre} falls inside {@code [inner, outer]}. */
    public static List<LivingEntity> victims(List<LivingEntity> candidates, Vec3 centre,
                                             double inner, double outer) {
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity candidate : candidates) {
            if (isInBand(centre, candidate.position(), inner, outer)) {
                hit.add(candidate);
            }
        }
        return hit;
    }

    /**
     * Knockback impulse pointing away from {@code centre}, with a fixed upward component. A victim
     * standing exactly on the centre is thrown straight up rather than in a random direction.
     */
    public static Vec3 knockback(Vec3 centre, Vec3 victim, double strength) {
        double dx = victim.x - centre.x;
        double dz = victim.z - centre.z;
        double lengthSq = dx * dx + dz * dz;
        if (lengthSq < 1.0E-6D) {
            return new Vec3(0.0D, KNOCKBACK_UP, 0.0D);
        }
        double length = Math.sqrt(lengthSq);
        return new Vec3(dx / length * strength, KNOCKBACK_UP, dz / length * strength);
    }

    /**
     * One tick of the ring: damages and launches everything in the band, then draws it.
     *
     * @param tickSinceImpact which tick of the expansion this is, counting from 1. The ring is only
     *                        <em>cracked open</em> once, on tick 1; later ticks are silent.
     * @param alreadyHit      ids that have been hit by this shockwave already; mutated in place so
     *                        no entity is hit twice by the same slam
     */
    public static void apply(ServerLevel level, LivingEntity source, Vec3 centre, double leadingEdge,
                             int tickSinceImpact, float damage, double knockbackStrength,
                             Set<UUID> alreadyHit, Predicate<LivingEntity> canHit) {
        double inner = Math.max(0.0D, leadingEdge - BAND_WIDTH);
        // The camera filter is applied here rather than left to the caller: a shockwave that
        // launches the creative-mode camera operator is not a bug any individual caller can be
        // trusted to remember. See Combatants.
        List<LivingEntity> candidates = Selection.around(level, LivingEntity.class, centre,
                leadingEdge + 2.0D,
                e -> e != source && !Combatants.isCamera(e) && canHit.test(e));

        for (LivingEntity victim : victims(candidates, centre, inner, leadingEdge)) {
            if (!alreadyHit.add(victim.getUUID())) {
                continue;
            }
            victim.hurt(level.damageSources().mobAttack(source), damage);
            Vec3 impulse = knockback(centre, victim.position(), knockbackStrength);
            victim.setDeltaMovement(victim.getDeltaMovement().add(impulse));
            victim.hurtMarked = true;
        }

        drawRing(level, centre, leadingEdge, tickSinceImpact);
    }

    /**
     * Block-crack particles along the ring, plus - on the impact tick only - four vanilla
     * "block broken" events for the crack.
     *
     * <p>{@code levelEvent(2001, ...)} is a full-volume block-break sound <em>and</em> a particle
     * burst. Firing it on every tick of the expansion stacked {@value #EXPANSION_TICKS} x 8
     * overlapping basalt cracks into 0.4 seconds, which on camera is white noise rather than a
     * slam, so the audio is spent once, on the tick the fist lands, and the following ticks are the
     * ring travelling outwards in silence.
     *
     * @param tickSinceImpact 1 on the tick of impact, then 2..{@value #EXPANSION_TICKS}
     */
    public static void drawRing(ServerLevel level, Vec3 centre, double radius, int tickSinceImpact) {
        BlockPos floorPos = BlockPos.containing(centre.x, centre.y - 0.2D, centre.z);
        BlockState floor = level.getBlockState(floorPos);
        if (floor.isAir()) {
            floor = level.getBlockState(floorPos.below());
        }
        BlockParticleOption crack = new BlockParticleOption(ParticleTypes.BLOCK, floor);
        boolean impactTick = tickSinceImpact <= 1;

        Vec3[] points = MathUtil.ring(centre, radius, RING_POINTS);
        for (int i = 0; i < points.length; i++) {
            Vec3 point = points[i];
            level.sendParticles(crack, point.x, point.y + 0.2D, point.z, 1, 0.15D, 0.25D, 0.15D, 0.05D);
            if (i % ACCENT_EVERY != 0) {
                continue;
            }
            level.sendParticles(ParticleTypes.LARGE_SMOKE, point.x, point.y + 0.3D, point.z,
                    1, 0.1D, 0.1D, 0.1D, 0.01D);
            if (impactTick) {
                level.levelEvent(2001, BlockPos.containing(point.x, point.y, point.z),
                        Block.getId(floor));
            }
        }
    }

    private Shockwave() {
    }
}
