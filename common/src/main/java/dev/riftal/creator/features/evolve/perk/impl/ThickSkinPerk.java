package dev.riftal.creator.features.evolve.perk.impl;

import dev.riftal.creator.features.evolve.perk.StagePerk;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Stage 2 - Runt. Resistance I for as long as the player is below half health. Small, but it is the
 * difference between surviving a creeper and not, which is the point of the stage.
 */
public final class ThickSkinPerk implements StagePerk {

    /** Refreshed every heartbeat, so it lapses about a second after the player heals back up. */
    private static final int EFFECT_TICKS = 25;

    @Override
    public String key() {
        return "thick_skin";
    }

    @Override
    public void tick(ServerPlayer player) {
        if (player.isSpectator() || !player.isAlive()) {
            return;
        }
        if (player.getHealth() < player.getMaxHealth() * 0.5F) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, EFFECT_TICKS, 0,
                    true, false, true));
        }
    }
}
