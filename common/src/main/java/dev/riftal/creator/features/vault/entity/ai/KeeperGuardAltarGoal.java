package dev.riftal.creator.features.vault.entity.ai;

import dev.riftal.creator.features.vault.entity.VaultKeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Keeps a bound Vault Keeper in its treasure room.
 *
 * <p>With no target and more than {@link VaultKeeper#LEASH_RADIUS} blocks between it and its altar
 * the Keeper paths home; past {@link VaultKeeper#TETHER_RADIUS} it is pulled back outright, so a
 * player cannot kite it up the entrance shaft and leave the room empty for the next take.
 */
public class KeeperGuardAltarGoal extends Goal {

    private static final int REPATH_INTERVAL = 20;

    private final VaultKeeper keeper;
    private final double speedModifier;
    private int repathCooldown;

    public KeeperGuardAltarGoal(VaultKeeper keeper, double speedModifier) {
        this.keeper = keeper;
        this.speedModifier = speedModifier;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.keeper.getTarget() != null) {
            return false;
        }
        BlockPos altar = this.keeper.altarPos();
        return altar != null && distanceSqrToAltar(altar) > VaultKeeper.LEASH_RADIUS * VaultKeeper.LEASH_RADIUS;
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos altar = this.keeper.altarPos();
        if (altar == null || this.keeper.getTarget() != null) {
            return false;
        }
        double close = VaultKeeper.LEASH_RADIUS * 0.5D;
        return distanceSqrToAltar(altar) > close * close;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
    }

    @Override
    public void stop() {
        this.keeper.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }

    @Override
    public void tick() {
        BlockPos altar = this.keeper.altarPos();
        if (altar == null) {
            return;
        }
        double distanceSqr = distanceSqrToAltar(altar);
        if (distanceSqr > VaultKeeper.TETHER_RADIUS * VaultKeeper.TETHER_RADIUS) {
            this.keeper.getNavigation().stop();
            this.keeper.teleportTo(altar.getX() + 0.5D, altar.getY() + 1.0D, altar.getZ() + 0.5D);
            return;
        }
        if (this.repathCooldown > 0) {
            this.repathCooldown--;
            return;
        }
        this.repathCooldown = REPATH_INTERVAL;
        this.keeper.getNavigation().moveTo(altar.getX() + 0.5D, altar.getY() + 1.0D,
                altar.getZ() + 0.5D, this.speedModifier);
    }

    private double distanceSqrToAltar(BlockPos altar) {
        return this.keeper.distanceToSqr(altar.getX() + 0.5D, altar.getY() + 0.5D, altar.getZ() + 0.5D);
    }
}
