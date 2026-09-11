package dev.riftal.creator.features.powers.ability.impl;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.powers.PowersFeature;
import dev.riftal.creator.features.powers.ability.Ability;
import dev.riftal.creator.features.powers.ability.AbilityContext;
import dev.riftal.creator.features.powers.effect.ActiveEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Slot 5. Ten seconds of hard cover: four golden hearts of absorption, Resistance II, a fat armour
 * bonus, and a particle shell that eats hostile projectiles before they arrive.
 *
 * <p>Melee still lands (reduced) - that is deliberate, it keeps the shot readable: arrows sparkle
 * out mid-air, a creeper does nothing, a zombie can still walk up and chip at you.
 *
 * <p>The per-tick half of the dome (projectile voiding, the shell, expiry) lives in
 * {@code ActiveEffects}; this class only raises it.
 */
public final class ShieldDomeAbility implements Ability {

    private static final int DURATION_TICKS = 200;
    private static final float ABSORPTION = 8.0F;
    private static final int RESISTANCE_AMPLIFIER = 1;

    @Override
    public String path() {
        return "shield_dome";
    }

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(PowersFeature.NAMESPACE, path());
    }

    @Override
    public int cooldownTicks() {
        return 400;
    }

    @Override
    public void activate(AbilityContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = context.level();

        ActiveEffects.startDome(player, context.gameTime(), DURATION_TICKS, ABSORPTION);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DURATION_TICKS,
                RESISTANCE_AMPLIFIER, false, false, true));

        Fx.sound(level, player.position(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 1.4F);
    }

    /** Dome duration in ticks. Read by the GameTests, so they never hard-code the timer. */
    public static int durationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public int hudColor() {
        return 0xFF6FE3FF;
    }
}
