package dev.riftal.creator.features.evolve.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.progression.XpSources;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The two evolution XP sources, caught where both loaders can see them.
 *
 * <p>Core has no death or eat event, and neither loader's own event API is reachable from
 * {@code common}, so these two injections are the loader-neutral hook. Both are pure side effects -
 * nothing is cancelled, nothing is redirected.
 *
 * <ul>
 *   <li>{@code LivingEntity#die} at TAIL: credit the killing player. {@code Mob} does not override
 *       {@code die}, so this covers every mob, and {@code ServerPlayer#die} calls {@code super}.</li>
 *   <li>{@code LivingEntity#eat} at HEAD: credit the eating player. {@code Player#eat} calls
 *       {@code super.eat(...)}, so a player finishing a meal lands here.</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class EvolveLivingEntityMixin {

    /**
     * {@code die} runs its body only once (it guards on {@code this.dead}) but a second call still
     * reaches TAIL, so the award is latched here instead.
     */
    @Unique
    private boolean creator_evolve$killXpAwarded;

    @Inject(
            method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("TAIL"))
    private void creator_evolve$awardKillXp(DamageSource damageSource, CallbackInfo ci) {
        if (!CreatorMods.isEnabled(EvolveFeature.ID)) {
            return;
        }
        if (creator_evolve$killXpAwarded) {
            return;
        }
        creator_evolve$killXpAwarded = true;
        XpSources.onKill((LivingEntity) (Object) this, damageSource);
    }

    @Inject(
            method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"))
    private void creator_evolve$awardFoodXp(Level level, ItemStack food, FoodProperties foodProperties,
                                            CallbackInfoReturnable<ItemStack> cir) {
        if (!CreatorMods.isEnabled(EvolveFeature.ID)) {
            return;
        }
        XpSources.onEat((LivingEntity) (Object) this, foodProperties);
    }
}
