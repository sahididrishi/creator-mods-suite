package dev.riftal.creator.features.rules.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.rules.RulesFeature;
import dev.riftal.creator.features.rules.hooks.RuleHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * {@code random_drops}' mob half.
 *
 * <p>{@code dropFromLootTable} is the one place a dying mob rolls its death loot, and vanilla has
 * already checked {@code doMobLoot} and {@code shouldDropLoot} before calling it - so cancelling it
 * and spawning the mapped item instead keeps every vanilla condition intact.
 */
@Mixin(LivingEntity.class)
public abstract class RulesLivingEntityMixin {

    @Inject(
            method = "dropFromLootTable(Lnet/minecraft/world/damagesource/DamageSource;Z)V",
            at = @At("HEAD"),
            cancellable = true)
    private void creator_rules$randomDeathDrops(DamageSource damageSource, boolean hitByPlayer,
                                                CallbackInfo ci) {
        if (!CreatorMods.isEnabled(RulesFeature.ID)) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        List<ItemStack> replacement = RuleHooks.remapMobDrops(self);
        if (replacement == null) {
            return;
        }
        for (ItemStack stack : replacement) {
            if (!stack.isEmpty()) {
                self.spawnAtLocation(stack.copy());
            }
        }
        ci.cancel();
    }
}
