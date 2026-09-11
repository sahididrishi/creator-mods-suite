package dev.riftal.creator.features.arsenal.client;

import dev.riftal.creator.features.arsenal.ArsenalFeature;
import dev.riftal.creator.features.arsenal.mixin.ArsenalItemPropertiesMixin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * The Storm Bow's draw animation: vanilla's {@code minecraft:pull} and {@code minecraft:pulling}
 * model predicates, registered for our bow.
 *
 * <p>Both function bodies are vanilla's own, copied out of {@code ItemProperties}' static
 * initialiser (1.21.1, lines 97-113) rather than approximated, so the three
 * {@code storm_bow_pulling_*} models switch on exactly the same thresholds as every other bow in
 * the game:
 *
 * <pre>{@code
 * register(Items.BOW, ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) -> {
 *     if (entity == null) {
 *         return 0.0F;
 *     } else {
 *         return entity.getUseItem() != stack ? 0.0F
 *                 : (float)(stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0F;
 *     }
 * });
 * register(Items.BOW, ResourceLocation.withDefaultNamespace("pulling"), (stack, level, entity, seed) ->
 *         entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
 * }</pre>
 *
 * <p><b>Client only.</b> Reached from {@code ArsenalFeature#initClient()} and nowhere else; it
 * touches {@code net.minecraft.client.renderer.item}, which does not exist on a dedicated server.
 * Registration is a map {@code put}, so the repeated {@code initClient()} calls NeoForge makes are
 * harmless.
 */
public final class StormBowProperties {

    /** Vanilla's draw-progress predicate id, 0.0 at rest to 1.0 at full draw over 20 ticks. */
    public static final ResourceLocation PULL = ResourceLocation.withDefaultNamespace("pull");

    /** Vanilla's "is being drawn at all" predicate id. */
    public static final ResourceLocation PULLING = ResourceLocation.withDefaultNamespace("pulling");

    /** Registers both predicates against the Storm Bow. Call from {@code initClient()}. */
    public static void register() {
        Item bow = ArsenalFeature.STORM_BOW.get();

        ArsenalItemPropertiesMixin.creator_arsenal$register(bow, PULL, (stack, level, entity, seed) -> {
            if (entity == null || entity.getUseItem() != stack) {
                return 0.0F;
            }
            return (float) (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0F;
        });

        ArsenalItemPropertiesMixin.creator_arsenal$register(bow, PULLING, (stack, level, entity, seed) ->
                entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
    }

    private StormBowProperties() {
    }
}
