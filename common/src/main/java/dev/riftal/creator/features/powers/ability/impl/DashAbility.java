package dev.riftal.creator.features.powers.ability.impl;

import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.core.util.MathUtil;
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
import net.minecraft.world.phys.Vec3;

/**
 * Slot 1. A flat 10-12 block streak in the direction the player is looking, with a short melee
 * i-frame window so the creator can dash through a mob pile without taking a hit.
 *
 * <p>Sneaking turns it into a full 3D dash (useful for going up or down on camera); standing it is
 * flattened onto the horizontal plane so it reads as a ground streak instead of a jump.
 */
public final class DashAbility implements Ability {

    private static final double SPEED = 1.6D;
    private static final double LIFT = 0.15D;
    private static final int INVULNERABLE_TICKS = 8;
    private static final int TRAIL_TICKS = 8;

    @Override
    public String path() {
        return "dash";
    }

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(PowersFeature.NAMESPACE, path());
    }

    @Override
    public int cooldownTicks() {
        return 60;
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        return !player.isInWater() && !player.isInLava() && !player.isPassenger();
    }

    @Override
    public void activate(AbilityContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = context.level();

        Vec3 direction = player.isShiftKeyDown() ? context.look() : MathUtil.horizontalLook(player);
        AbilityFx.launch(player, direction.scale(SPEED).add(0.0D, LIFT, 0.0D));
        player.fallDistance = 0.0F;
        ActiveEffects.startDashInvulnerability(player, context.gameTime() + INVULNERABLE_TICKS);

        Fx.sound(level, player.position(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.6F, 1.4F);
        Fx.particles(level, ParticleTypes.CLOUD, player.position(), 8, 0.3D, 0.02D);

        TickScheduler.runRepeating(1, TRAIL_TICKS, task -> {
            if (player.isRemoved() || !(player.level() instanceof ServerLevel current)) {
                task.cancel();
                return;
            }
            Fx.particles(current, ParticleTypes.END_ROD,
                    player.position().add(0.0D, 0.4D, 0.0D), 12, 0.2D, 0.02D);
        }).tag(PowersFeature.taskTag());
    }

    @Override
    public int hudColor() {
        return 0xFF7FD7FF;
    }
}
