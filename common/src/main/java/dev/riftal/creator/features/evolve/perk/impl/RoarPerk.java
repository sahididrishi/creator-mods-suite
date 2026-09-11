package dev.riftal.creator.features.evolve.perk.impl;

import dev.riftal.creator.core.util.Selection;
import dev.riftal.creator.features.evolve.perk.StagePerk;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Stage 5 - Apex. {@code /evolve roar} scatters everything nearby: mobs within ten blocks drop
 * their target, take Weakness II for five seconds and get shoved away from the beast.
 */
public final class RoarPerk implements StagePerk {

    /** How far the roar carries, in blocks. */
    public static final double RADIUS = 10.0D;

    /** Duration of the Weakness applied to everything in range, in ticks. */
    public static final int WEAKNESS_TICKS = 100;

    @Override
    public String key() {
        return "roar";
    }

    @Override
    public int onRoar(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        Vec3 centre = player.position();
        List<Mob> nearby = Selection.around(level, Mob.class, centre, RADIUS, mob -> true);
        for (Mob mob : nearby) {
            mob.setTarget(null);
            mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS, 1,
                    false, true, true));
            mob.knockback(1.1D, centre.x - mob.getX(), centre.z - mob.getZ());
        }
        return nearby.size();
    }
}
