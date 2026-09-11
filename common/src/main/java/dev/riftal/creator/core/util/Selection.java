package dev.riftal.creator.core.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/** Entity selection around a point. All server side. */
public final class Selection {

    /** Entities of {@code type} inside a sphere of {@code radius} that pass {@code filter}. */
    public static <T extends Entity> List<T> around(Level level, Class<T> type, Vec3 centre,
                                                    double radius, Predicate<? super T> filter) {
        double rSq = radius * radius;
        return level.getEntitiesOfClass(type, MathUtil.boxAround(centre, radius),
                e -> e.isAlive() && e.position().distanceToSqr(centre) <= rSq && filter.test(e));
    }

    /** Living entities in a sphere, excluding {@code except}. */
    public static List<LivingEntity> livingAround(Level level, Vec3 centre, double radius, Entity except) {
        return around(level, LivingEntity.class, centre, radius, e -> e != except);
    }

    /** Players in a sphere. */
    public static List<ServerPlayer> playersAround(ServerLevel level, Vec3 centre, double radius) {
        return around(level, ServerPlayer.class, centre, radius, p -> !p.isSpectator());
    }

    /** Nearest entity of {@code type} in a sphere, or null. */
    public static <T extends Entity> T nearest(Level level, Class<T> type, Vec3 centre,
                                               double radius, Predicate<? super T> filter) {
        T best = null;
        double bestSq = Double.MAX_VALUE;
        for (T candidate : around(level, type, centre, radius, filter)) {
            double distSq = candidate.position().distanceToSqr(centre);
            if (distSq < bestSq) {
                bestSq = distSq;
                best = candidate;
            }
        }
        return best;
    }

    private Selection() {
    }
}
