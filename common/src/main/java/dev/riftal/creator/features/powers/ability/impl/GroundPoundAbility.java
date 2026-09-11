package dev.riftal.creator.features.powers.ability.impl;

import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.powers.PowersFeature;
import dev.riftal.creator.features.powers.ability.Ability;
import dev.riftal.creator.features.powers.ability.AbilityContext;
import dev.riftal.creator.features.powers.ability.AbilityFx;
import dev.riftal.creator.features.powers.effect.ActiveEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Slot 3. Only usable in mid-air: the player is popped up for six ticks, then rockets straight
 * down. The landing is detected by {@code ActiveEffects}, which calls back into
 * {@code PowerManager} to run the shockwave - keeping the "did I land yet" poll in the one place
 * that already ticks.
 */
public final class GroundPoundAbility implements Ability {

    private static final int LEAP_TICKS = 6;
    private static final int SLAM_TIMEOUT_TICKS = 40;
    private static final double LEAP_SPEED = 0.9D;
    private static final double SLAM_SPEED = -2.5D;

    @Override
    public String path() {
        return "ground_pound";
    }

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(PowersFeature.NAMESPACE, path());
    }

    @Override
    public int cooldownTicks() {
        return 160;
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        return !player.onGround()
                && !player.isInWater()
                && !player.isInLava()
                && !player.getAbilities().flying
                && !player.isPassenger();
    }

    @Override
    public void activate(AbilityContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = context.level();

        // An elytra pose survives a velocity change, so stop the glide first or the client keeps
        // the gliding animation all the way into the floor.
        player.stopFallFlying();

        AbilityFx.launch(player, new Vec3(0.0D, LEAP_SPEED, 0.0D));
        ActiveEffects.startPound(player, context.gameTime() + LEAP_TICKS + SLAM_TIMEOUT_TICKS);
        Fx.sound(level, player.position(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.5F, 1.8F);

        TickScheduler.runLater(LEAP_TICKS, () -> {
            if (player.isRemoved() || !ActiveEffects.pounding(player.getUUID())) {
                return;
            }
            if (!(player.level() instanceof ServerLevel current)) {
                ActiveEffects.cancelPound(player.getUUID());
                return;
            }
            AbilityFx.launch(player, new Vec3(0.0D, SLAM_SPEED, 0.0D));
            player.fallDistance = 0.0F;
            ActiveEffects.armPound(player, current.getGameTime() + SLAM_TIMEOUT_TICKS);
            Fx.sound(current, player.position(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.8F);
        }).tag(PowersFeature.taskTag());
    }

    @Override
    public int hudColor() {
        return 0xFFC2A16B;
    }
}
