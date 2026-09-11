package dev.riftal.creator.features.powers.ability.impl;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.core.util.Selection;
import dev.riftal.creator.features.powers.PowersFeature;
import dev.riftal.creator.features.powers.ability.Ability;
import dev.riftal.creator.features.powers.ability.AbilityContext;
import dev.riftal.creator.features.powers.ability.AbilityFx;
import dev.riftal.creator.features.powers.effect.ActiveEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

import java.util.List;

/**
 * Slot 6. Every hostile mob within 12 blocks stops dead for five seconds, coated in snowflakes.
 * They keep their vanilla shiver (borrowed from powder snow) so the pause reads on camera even for
 * a mob that was standing still anyway.
 *
 * <p>Only {@code Enemy} mobs are affected: the creator's horse, the villagers and the cows in shot
 * carry on as normal. Bosses are exempt.
 */
public final class MobFreezeAbility implements Ability {

    private static final double RADIUS = 12.0D;
    private static final int DURATION_TICKS = 100;

    /** Hard cap so a mob-farm-sized crowd cannot make one keypress cost a tick. */
    private static final int MAX_TARGETS = 64;

    @Override
    public String path() {
        return "mob_freeze";
    }

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(PowersFeature.NAMESPACE, path());
    }

    @Override
    public int cooldownTicks() {
        return 300;
    }

    @Override
    public void activate(AbilityContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = context.level();
        long until = context.gameTime() + DURATION_TICKS;

        List<Mob> targets = Selection.around(level, Mob.class, player.position(), RADIUS,
                mob -> mob instanceof Enemy && mob.isAlive() && !AbilityFx.isBoss(mob));

        int frozen = 0;
        for (Mob mob : targets) {
            if (frozen >= MAX_TARGETS) {
                break;
            }
            ActiveEffects.freeze(mob, until);
            Fx.particles(level, ParticleTypes.SNOWFLAKE,
                    mob.position().add(0.0D, mob.getBbHeight() * 0.5D, 0.0D), 30, 0.45D, 0.02D);
            Fx.sound(level, mob.position(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.5F, 1.6F);
            frozen++;
        }

        Fx.particleRing(level, ParticleTypes.SNOWFLAKE, player.position().add(0.0D, 0.2D, 0.0D), RADIUS, 48);
        Fx.sound(level, player.position(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.9F, 0.7F);
    }

    /** Freeze duration in ticks. Read by the commands and the GameTests. */
    public static int durationTicks() {
        return DURATION_TICKS;
    }

    /** Freeze radius in blocks. Read by the GameTests. */
    public static double radius() {
        return RADIUS;
    }

    @Override
    public int hudColor() {
        return 0xFFDCF3FF;
    }
}
