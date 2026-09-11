package dev.riftal.creator.features.evolve.perk.impl;

import dev.riftal.creator.features.evolve.perk.StagePerk;
import dev.riftal.creator.features.evolve.perk.StagePerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stage 1 - Hatchling. Crouch on dirt, sand or snow for a second and you vanish; stand up, move off
 * soft ground or stop sneaking and you fade back in within two seconds.
 */
public final class BurrowPerk implements StagePerk {

    /** Ticks of continuous crouching on soft ground before the player goes invisible. */
    private static final int CHARGE_TICKS = 20;

    /** How long each refresh of the invisibility lasts. Longer than the tick period, so it never flickers. */
    private static final int EFFECT_TICKS = 40;

    private final Map<UUID, Integer> charge = new HashMap<>();

    @Override
    public String key() {
        return "burrow";
    }

    @Override
    public void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!qualifies(player)) {
            charge.remove(id);
            return;
        }
        int charged = charge.merge(id, StagePerks.TICK_PERIOD, Integer::sum);
        if (charged >= CHARGE_TICKS) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, EFFECT_TICKS, 0,
                    true, false, true));
        }
    }

    @Override
    public void revoke(ServerPlayer player) {
        charge.remove(player.getUUID());
    }

    private static boolean qualifies(ServerPlayer player) {
        if (!player.isShiftKeyDown() || !player.onGround() || player.isSpectator()) {
            return false;
        }
        BlockState below = player.getBlockStateOn();
        return below.is(BlockTags.DIRT) || below.is(BlockTags.SAND) || below.is(BlockTags.SNOW);
    }
}
