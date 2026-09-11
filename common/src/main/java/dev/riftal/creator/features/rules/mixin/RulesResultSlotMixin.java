package dev.riftal.creator.features.rules.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.rules.RulesFeature;
import dev.riftal.creator.features.rules.hooks.RuleHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code crafts_x10}'s hook.
 *
 * <p>{@code ResultSlot} backs both the crafting table and the 2x2 inventory grid, and {@code onTake}
 * runs once per stack taken - including once per stack when shift-clicking - which is exactly the
 * granularity the multiplier wants. {@code RuleHooks} drops the client-side call.
 */
@Mixin(ResultSlot.class)
public abstract class RulesResultSlotMixin {

    @Inject(
            method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("TAIL"))
    private void creator_rules$afterCraftTaken(Player player, ItemStack stack, CallbackInfo ci) {
        if (!CreatorMods.isEnabled(RulesFeature.ID)) {
            return;
        }
        RuleHooks.onCraftTaken(player, stack);
    }
}
