package dev.riftal.creator.features.evolve.mixin;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.stage.StageModifiers;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The way back out of Evolve.
 *
 * <p>Stage modifiers are written with {@code addPermanentModifier} (see {@link StageModifiers} for
 * why), which means they serialise into the player's vanilla {@code attributes} NBT and survive the
 * feature being switched off in {@code config/creatormods.json}. Without this hook, flipping
 * {@code "evolve": false} would leave every player who had ever joined pinned at 0.6x scale and 16
 * max health with no command, no heartbeat and no lifecycle call left to undo it - a disabled
 * feature gets none of those.
 *
 * <p>So the undo lives where it is guaranteed to run: the vanilla attribute load path. Note the
 * <em>inverted</em> guard. Every other injection in this feature bails out when the feature is off;
 * this one only does anything when it is off, and all it does is delete the ten fixed
 * {@code creator_evolve:*} modifier ids this feature wrote itself. That restores vanilla behaviour
 * rather than changing it, which is the spirit of CONTRACT.md section 10.3. Nothing else is touched:
 * the {@code EvolutionData} attachment is left alone, so turning the feature back on and rejoining
 * puts the player back on the rung they were standing on.
 */
@Mixin(AttributeMap.class)
public abstract class EvolveAttributeMapMixin {

    @Inject(method = "load(Lnet/minecraft/nbt/ListTag;)V", at = @At("TAIL"))
    private void creator_evolve$stripStageModifiersWhileDisabled(ListTag nbt, CallbackInfo ci) {
        if (CreatorMods.isEnabled(EvolveFeature.ID)) {
            return;
        }
        int removed = StageModifiers.stripFrom((AttributeMap) (Object) this);
        if (removed > 0) {
            LOG.info("[evolve] feature is off: removed {} leftover stage attribute modifier(s) "
                    + "while loading an entity", removed);
        }
    }
}
