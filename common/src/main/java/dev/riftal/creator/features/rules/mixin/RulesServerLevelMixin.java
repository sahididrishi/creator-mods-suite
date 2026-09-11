package dev.riftal.creator.features.rules.mixin;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.rules.RulesFeature;
import dev.riftal.creator.features.rules.hooks.RuleHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code giant_mobs}' spawn hook.
 *
 * <p>{@code ServerLevel#addFreshEntity} is the single funnel for every "this entity has just
 * appeared" case a rule cares about: natural spawns, spawners, spawn eggs, structure pieces and
 * {@code /summon} - {@code ServerLevelAccessor#addFreshEntityWithPassengers} forwards straight into
 * it. A mob that shows up on camera is now giant on the frame it appears instead of up to a second
 * later, which is exactly the beat the plan's shot list asks for.
 *
 * <p>Entities coming back from a chunk load do <em>not</em> pass through here, which is why the
 * rule keeps its slow sweep as a backstop.
 */
@Mixin(ServerLevel.class)
public abstract class RulesServerLevelMixin {

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"))
    private void creator_rules$onEntityJoin(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!CreatorMods.isEnabled(RulesFeature.ID)) {
            return;
        }
        if (!cir.getReturnValueZ()) {
            return;
        }
        RuleHooks.onEntityJoin((ServerLevel) (Object) this, entity);
    }
}
