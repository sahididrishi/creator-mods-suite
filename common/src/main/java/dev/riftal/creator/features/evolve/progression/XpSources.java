package dev.riftal.creator.features.evolve.progression;

import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.net.XpPopupPayload;
import dev.riftal.creator.features.evolve.stage.StageMath;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;

/**
 * Where evolution XP comes from: things you kill and things you eat.
 *
 * <p>Both entry points are called from {@code EvolveLivingEntityMixin}, which is the only
 * loader-neutral place to catch {@code LivingEntity#die} and {@code LivingEntity#eat}. Everything
 * here is server side and bails out early when the feature is off.
 */
public final class XpSources {

    /**
     * Called at the end of {@code LivingEntity#die}. Credits the killing player, if there was one.
     */
    public static void onKill(LivingEntity victim, DamageSource source) {
        if (!EvolveFeature.isReady() || !(victim.level() instanceof ServerLevel)) {
            return;
        }
        Entity attacker = source.getEntity();
        if (!(attacker instanceof ServerPlayer killer) || killer == victim) {
            return;
        }
        if (killer.isSpectator()) {
            return;
        }

        int xp = StageMath.xpForKill(victim instanceof Player, isBoss(victim),
                victim instanceof Enemy, victim.getMaxHealth());
        EvolveManager.recordKill(killer);
        EvolveManager.addXp(killer, xp, XpPopupPayload.SOURCE_KILL);
    }

    /**
     * Called at the start of {@code LivingEntity#eat}, which players reach through
     * {@code Player#eat}. Only real food counts; nutrition 0 items award nothing.
     */
    public static void onEat(LivingEntity eater, FoodProperties food) {
        if (!EvolveFeature.isReady() || !(eater.level() instanceof ServerLevel)) {
            return;
        }
        if (!(eater instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        int xp = StageMath.xpForFood(food.nutrition());
        if (xp > 0) {
            EvolveManager.addXp(player, xp, XpPopupPayload.SOURCE_FOOD);
        }
    }

    /** The three vanilla entities that count as a boss kill. */
    public static boolean isBoss(LivingEntity victim) {
        return victim instanceof EnderDragon || victim instanceof WitherBoss || victim instanceof Warden;
    }

    private XpSources() {
    }
}
