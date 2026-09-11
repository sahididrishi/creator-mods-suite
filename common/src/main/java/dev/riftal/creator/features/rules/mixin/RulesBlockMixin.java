package dev.riftal.creator.features.rules.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.rules.RulesFeature;
import dev.riftal.creator.features.rules.hooks.RuleHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * {@code random_drops}' block half.
 *
 * <p>Both static {@code Block#getDrops} overloads funnel every block drop in the game - mining,
 * explosions, pistons, {@code /setblock destroy} - through one list, so replacing the returned list
 * is a complete and side-effect-free swap. Doing it here rather than by rewriting loot tables means
 * the rule takes effect on the very next block instead of needing a resource reload.
 */
@Mixin(Block.class)
public abstract class RulesBlockMixin {

    @ModifyReturnValue(
            method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;",
            at = @At("RETURN"))
    private static List<ItemStack> creator_rules$remapDrops(List<ItemStack> original, BlockState state,
                                                            ServerLevel level, BlockPos pos,
                                                            BlockEntity blockEntity) {
        if (!CreatorMods.isEnabled(RulesFeature.ID)) {
            return original;
        }
        return RuleHooks.remapBlockDrops(original, state, level, pos);
    }

    @ModifyReturnValue(
            method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;"
                    + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
            at = @At("RETURN"))
    private static List<ItemStack> creator_rules$remapDropsWithTool(List<ItemStack> original, BlockState state,
                                                                    ServerLevel level, BlockPos pos,
                                                                    BlockEntity blockEntity, Entity entity,
                                                                    ItemStack tool) {
        if (!CreatorMods.isEnabled(RulesFeature.ID)) {
            return original;
        }
        return RuleHooks.remapBlockDrops(original, state, level, pos);
    }
}
