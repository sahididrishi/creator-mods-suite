package dev.riftal.creator.features.vault.entity.ai;

import dev.riftal.creator.features.vault.entity.VaultKeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Walks an idle Vault Keeper back to its altar.
 *
 * <p>This is the <em>soft</em> half of the leash only, and it deliberately stands down while the
 * Keeper has a target so it never fights {@code MeleeAttackGoal} for {@link Goal.Flag#MOVE}. The
 * hard tether that stops a player kiting the Keeper up the entrance shaft cannot live here for
 * exactly that reason - a kiting player <em>is</em> the target, so this goal is not running - and
 * is in {@code VaultKeeper#customServerAiStep()} instead, where it runs every tick regardless.
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
