package dev.riftal.creator.features.rules.rules;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure "has this player moved?" bookkeeping for {@code no_stop_moving}.
 *
 * <p>Movement under {@link #THRESHOLD_SQR} (2 cm) counts as standing still. The counter can go
 * negative: {@link #grace} pushes it below zero so a player who just joined, respawned or changed
 * dimension gets a breather before the rule starts biting.
 *
 * <p>No Minecraft types, so it unit-tests without a game.
 */
public final class StillTracker {

    /** Squared distance below which a player counts as motionless: 0.02 blocks. */
    public static final double THRESHOLD_SQR = 0.0004D;

    private final Map<UUID, double[]> lastPos = new HashMap<>();
    private final Map<UUID, Integer> stillTicks = new HashMap<>();

    /**
     * Records one tick of movement.
     *
     * @return consecutive still ticks; negative while the player still has grace left
     */
    public int update(UUID id, double x, double y, double z) {
        double[] previous = lastPos.get(id);
        lastPos.put(id, new double[]{x, y, z});
        int current = stillTicks.getOrDefault(id, 0);
        if (previous == null) {
            stillTicks.put(id, current);
            return current;
        }
        double dx = x - previous[0];
        double dy = y - previous[1];
        double dz = z - previous[2];
        int updated = (dx * dx + dy * dy + dz * dz) < THRESHOLD_SQR ? current + 1 : 0;
        stillTicks.put(id, updated);
        return updated;
    }

    /** Gives the player {@code ticks} ticks of immunity before the still counter can reach zero. */
    public void grace(UUID id, int ticks) {
        stillTicks.put(id, -Math.abs(ticks));
        lastPos.remove(id);
    }

    /** Current still counter without advancing it. */
    public int stillTicks(UUID id) {
        return stillTicks.getOrDefault(id, 0);
    }

    public void forget(UUID id) {
        lastPos.remove(id);
        stillTicks.remove(id);
    }

    /** Drops every player that is not in {@code keep}. Called when the online set shrinks. */
    public void retainAll(Set<UUID> keep) {
        lastPos.keySet().retainAll(keep);
        stillTicks.keySet().retainAll(keep);
    }

    public void clear() {
        lastPos.clear();
        stillTicks.clear();
    }

    public int size() {
        return stillTicks.size();
    }
}
