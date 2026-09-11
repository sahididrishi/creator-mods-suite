package dev.riftal.creator.features.evolve.perk.impl;

import dev.riftal.creator.core.util.CooldownTracker;
import dev.riftal.creator.features.evolve.perk.StagePerk;
import dev.riftal.creator.features.evolve.perk.StagePerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stage 1 - Hatchling. Crouch on dirt, sand or snow for one second and you vanish for five, then
 * the burrow needs twenty seconds before it will take you again.
 *
 * <p>The numbers are the plan's stage table verbatim: 1 s charge, 5 s invisibility, 20 s cooldown.
 * The cooldown is the part that matters on camera - without it a Hatchling can sit on a dirt block
 * and stay invisible for as long as the take lasts, which is not a perk, it is a bug.
 */
public final class BurrowPerk implements StagePerk {

    /** Ticks of continuous crouching on soft ground before the player goes invisible. */
    public static final int CHARGE_TICKS = 20;

    /** How long one burrow lasts, in ticks. */
    public static final int EFFECT_TICKS = 100;

    /** Ticks before the burrow can be used again, counted from the moment it fires. */
    public static final int COOLDOWN_TICKS = 400;

    private final Map<UUID, Integer> charge = new HashMap<>();

    private final CooldownTracker<UUID> cooldown = new CooldownTracker<>();

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
        long now = player.level().getGameTime();
        if (!cooldown.ready(id, now)) {
            // Still on cooldown: crouching does not even start charging again.
            charge.remove(id);
            return;
        }
        int charged = charge.merge(id, StagePerks.TICK_PERIOD, Integer::sum);
        if (charged >= CHARGE_TICKS) {
            charge.remove(id);
            cooldown.start(id, now, COOLDOWN_TICKS);
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, EFFECT_TICKS, 0,
                    true, false, true));
        }
    }

    /** Ticks left before this player may burrow again; 0 when it is ready. */
    public int cooldownRemaining(ServerPlayer player) {
        return cooldown.remaining(player.getUUID(), player.level().getGameTime());
    }

    @Override
    public void revoke(ServerPlayer player) {
        charge.remove(player.getUUID());
        cooldown.clear(player.getUUID());
    }

    @Override
    public void clearAll() {
        charge.clear();
        cooldown.clearAll();
    }

    @Override
    public void retain(Set<UUID> onlinePlayerIds) {
        // The charge map is the one that grows every heartbeat, so it is the one that gets pruned.
        // CooldownTracker exposes no key set; its entries are one long per player who has ever
        // burrowed in this session, and clearAll() empties it on the next server start or /reload.
        charge.keySet().retainAll(onlinePlayerIds);
    }

    private static boolean qualifies(ServerPlayer player) {
        if (!player.isShiftKeyDown() || !player.onGround() || player.isSpectator()) {
            return false;
        }
        BlockState below = player.getBlockStateOn();
        return below.is(BlockTags.DIRT) || below.is(BlockTags.SAND) || below.is(BlockTags.SNOW);
    }
}
