package dev.riftal.creator.features.evolve.perk.impl;

import dev.riftal.creator.features.evolve.perk.StagePerk;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Stage 3 - Brute. Sprinting winds the Brute up: Strength I while running, which is what makes the
 * "one-shot a zombie mid-sprint" beat land on camera.
 */
public final class ChargePerk implements StagePerk {

    private static final int EFFECT_TICKS = 25;

    @Override
    public String key() {
        return "charge";
    }

    @Override
    public void tick(ServerPlayer player) {
        if (player.isSpectator() || !player.isAlive()) {
            return;
        }
        if (player.isSprinting()) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, EFFECT_TICKS, 0,
                    true, false, true));
        }
    }
}
