package dev.riftal.creator.features.arsenal.mixin;

import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Opens vanilla's private {@code ItemProperties#register} so the Storm Bow can carry the
 * {@code minecraft:pull} / {@code minecraft:pulling} model predicates plan 07 section 5 lists as MVP
 * registry content.
 *
 * <p>The two predicates are not generic: {@code ItemProperties}' static initialiser registers them
 * against {@code Items.BOW} specifically (lines 97 and 113 of the 1.21.1 source), and
 * {@code getProperty} only consults {@code GENERIC_PROPERTIES} before falling back to the
 * per-{@code Item} map, so a third-party bow cannot borrow them from JSON. The registration method
 * itself is {@code private static void register(Item, ResourceLocation, ClampedItemPropertyFunction)}
 * (line 53), which is why this exists.
 *
 * <p>Fabric would want a client access widener and NeoForge an access transformer - both shared
 * files no feature may edit. A static {@link Invoker} is the one spelling that is identical on both
 * loaders, which is exactly the argument {@code RulesGameRulesMixin} makes for {@code GameRules}.
 * Nothing is injected and nothing is overwritten.
 *
 * <p>Listed under {@code "client"} in {@code creatormods-arsenal.mixins.json}: {@code ItemProperties}
 * is {@code @OnlyIn(Dist.CLIENT)} and this is only ever called from {@code initClient()}.
 */
@Mixin(ItemProperties.class)
public interface ArsenalItemPropertiesMixin {

    /**
     * {@code private static void register(Item item, ResourceLocation name,
     * ClampedItemPropertyFunction property)} - verified in {@code ItemProperties.java:53}.
     */
    @Invoker("register")
    static void creator_arsenal$register(Item item, ResourceLocation name,
                                         ClampedItemPropertyFunction property) {
        throw new AssertionError("mixin did not apply");
    }
}
