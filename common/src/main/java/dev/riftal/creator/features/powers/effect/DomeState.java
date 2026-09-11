package dev.riftal.creator.features.powers.effect;

import net.minecraft.server.level.ServerPlayer;

/**
 * A live Shield Dome.
 *
 * @param player            the player under the dome. Held directly rather than looked up by UUID
 *                          each tick, so the per-tick cost is proportional to the number of live
 *                          domes and nothing else
 * @param until             absolute game tick the dome pops
 * @param absorptionGiven   absorption hearts this dome added, so expiry can take back exactly what
 *                          it gave and never eat a golden apple's absorption
 */
public record DomeState(ServerPlayer player, long until, float absorptionGiven) {
}
