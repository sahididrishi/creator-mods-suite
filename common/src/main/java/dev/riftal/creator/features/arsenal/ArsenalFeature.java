package dev.riftal.creator.features.arsenal;

import dev.riftal.creator.Constants;
import dev.riftal.creator.core.Feature;
import dev.riftal.creator.core.client.ClientRenderers;
import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.core.registry.Registrar;
import dev.riftal.creator.core.registry.RegistryEntry;
import dev.riftal.creator.features.arsenal.client.ArsenalClientFx;
import dev.riftal.creator.features.arsenal.client.GrappleHookRenderer;
import dev.riftal.creator.features.arsenal.client.StormArrowRenderer;
import dev.riftal.creator.features.arsenal.command.ArsenalCommands;
import dev.riftal.creator.features.arsenal.entity.GrappleHookEntity;
import dev.riftal.creator.features.arsenal.entity.StormArrowEntity;
import dev.riftal.creator.features.arsenal.item.ArsenalTiers;
import dev.riftal.creator.features.arsenal.item.GrappleBladeItem;
import dev.riftal.creator.features.arsenal.item.GravityHammerItem;
import dev.riftal.creator.features.arsenal.item.SoulScytheItem;
import dev.riftal.creator.features.arsenal.item.StormBowItem;
import dev.riftal.creator.features.arsenal.net.GrappleFxPayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;

/**
 * Arsenal - see {@code plans/07-arsenal.md}.
 *
 * <p>Four signature weapons, each with one unmistakable on-camera mechanic:
 * <ul>
 *   <li><b>Grapple Blade</b> - fires a hook and reels you to whatever it bites.</li>
 *   <li><b>Storm Bow</b> - full-draw arrows call a visual-only lightning bolt and a 3-block blast.</li>
 *   <li><b>Gravity Hammer</b> - lifts everything within six blocks, hangs it, then slams it down.</li>
 *   <li><b>Soul Scythe</b> - 25% lifesteal, an always-on sweep, and souls that become absorption.</li>
 * </ul>
 *
 * <p>This feature owns, and nothing else:
 * <ul>
 *   <li>{@code common/src/main/java/dev/riftal/creator/features/arsenal/**}</li>
 *   <li>{@code common/src/main/resources/assets/creator_arsenal/**} and {@code data/creator_arsenal/**}</li>
 *   <li>{@code common/src/main/resources/creatormods-arsenal.mixins.json}</li>
 *   <li>{@code common/src/test/java/dev/riftal/creator/features/arsenal/**}</li>
 *   <li>{@code fabric|neoforge/src/main/java/dev/riftal/creator/features/arsenal/**}</li>
 *   <li>{@code fabric/src/gametest/java/dev/riftal/creator/features/arsenal/**}</li>
 * </ul>
 * See {@code CONTRACT.md} in the repo root.
 */
public final class ArsenalFeature implements Feature {

    /** Feature id, resource namespace suffix and mixin config suffix. */
    public static final String ID = "arsenal";

    /** Resource namespace owned by this feature. */
    public static final String NAMESPACE = "creator_arsenal";

    /** Grapple Blade. Right-click fires the hook. */
    public static RegistryEntry<Item> GRAPPLE_BLADE;

    /** Storm Bow. Full draw calls lightning. */
    public static RegistryEntry<Item> STORM_BOW;

    /** Gravity Hammer. Right-click lifts and slams. */
    public static RegistryEntry<Item> GRAVITY_HAMMER;

    /** Soul Scythe. Lifesteal, always-on sweep, souls on kill. */
    public static RegistryEntry<Item> SOUL_SCYTHE;

    /** The Grapple Blade's hook projectile. */
    public static RegistryEntry<EntityType<GrappleHookEntity>> GRAPPLE_HOOK;

    /** The Storm Bow's arrow. */
    public static RegistryEntry<EntityType<StormArrowEntity>> STORM_ARROW;

    /** The one creative tab this feature owns. */
    public static RegistryEntry<CreativeModeTab> TAB;

    private static RegistryEntry<SoundEvent> hookBite;
    private static RegistryEntry<SoundEvent> slamImpact;
    private static RegistryEntry<SoundEvent> soulAbsorb;

    /** Metallic bite the grapple hook makes when it catches. */
    public static SoundEvent hookBite() {
        return hookBite.get();
    }

    /** Low thud under the Gravity Hammer's slam. */
    public static SoundEvent slamImpact() {
        return slamImpact.get();
    }

    /** Bright swallow when a soul wisp reaches its killer. */
    public static SoundEvent soulAbsorb() {
        return soulAbsorb.get();
    }

    /**
     * {@code creator_arsenal:<path>} as a static call, for code that has no {@link Feature} instance
     * to hand (attribute modifier ids, scheduler tags). The instance method {@code rl(String)} from
     * {@link Feature} does the same thing.
     */
    public static ResourceLocation res(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        Registrar<Item> items = registrar(Registries.ITEM);
        Registrar<EntityType<?>> entityTypes = registrar(Registries.ENTITY_TYPE);
        Registrar<CreativeModeTab> tabs = registrar(Registries.CREATIVE_MODE_TAB);
        Registrar<SoundEvent> sounds = registrar(Registries.SOUND_EVENT);

        GRAPPLE_BLADE = items.<Item>register(GrappleBladeItem.PATH, () ->
                new GrappleBladeItem(ArsenalTiers.GRAPPLE, new Item.Properties()
                        .rarity(Rarity.RARE)
                        .attributes(SwordItem.createAttributes(ArsenalTiers.GRAPPLE, 3, -2.4F))
                        .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

        STORM_BOW = items.<Item>register(StormBowItem.PATH, () ->
                new StormBowItem(new Item.Properties()
                        .durability(StormBowItem.DURABILITY)
                        .rarity(Rarity.EPIC)
                        .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

        GRAVITY_HAMMER = items.<Item>register(GravityHammerItem.PATH, () ->
                new GravityHammerItem(ArsenalTiers.GRAVITY, new Item.Properties()
                        .rarity(Rarity.EPIC)
                        .attributes(SwordItem.createAttributes(ArsenalTiers.GRAVITY, 3, -3.2F))
                        .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

        SOUL_SCYTHE = items.<Item>register(SoulScytheItem.PATH, () ->
                new SoulScytheItem(ArsenalTiers.SOUL, new Item.Properties()
                        .rarity(Rarity.EPIC)
                        .attributes(SoulScytheItem.attributes(ArsenalTiers.SOUL, 3, -2.8F))
                        .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

        GRAPPLE_HOOK = entityTypes.register("grapple_hook", () -> EntityType.Builder
                .<GrappleHookEntity>of(GrappleHookEntity::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .clientTrackingRange(4)
                .updateInterval(1)
                .noSave()
                .noSummon()
                .build("grapple_hook"));

        STORM_ARROW = entityTypes.register("storm_arrow", () -> EntityType.Builder
                .<StormArrowEntity>of(StormArrowEntity::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .eyeHeight(0.13F)
                .clientTrackingRange(4)
                .updateInterval(20)
                .noSummon()
                .build("storm_arrow"));

        hookBite = sounds.register("arsenal.hook_bite",
                () -> SoundEvent.createVariableRangeEvent(res("arsenal.hook_bite")));
        slamImpact = sounds.register("arsenal.slam_impact",
                () -> SoundEvent.createVariableRangeEvent(res("arsenal.slam_impact")));
        soulAbsorb = sounds.register("arsenal.soul_absorb",
                () -> SoundEvent.createVariableRangeEvent(res("arsenal.soul_absorb")));

        TAB = tabs.register(NAMESPACE, () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .title(Component.translatable("itemGroup." + NAMESPACE))
                .icon(() -> new ItemStack(SOUL_SCYTHE.get()))
                .displayItems((parameters, output) -> {
                    output.accept(GRAPPLE_BLADE.get());
                    output.accept(STORM_BOW.get());
                    output.accept(GRAVITY_HAMMER.get());
                    output.accept(SOUL_SCYTHE.get());
                })
                .build());

        // Cosmetic server-to-client hook effects. Registered on both sides so the payload type
        // exists on a dedicated server; the handler body only ever runs on a client.
        Payloads.registerS2C(GrappleFxPayload.TYPE, GrappleFxPayload.CODEC,
                payload -> ArsenalClientFx.play(payload));

        ArsenalCommands.register();
    }

    @Override
    public void initCommon() {
        Constants.LOG.info("[arsenal] four signature weapons armed: {}, {}, {}, {}",
                GRAPPLE_BLADE.id(), STORM_BOW.id(), GRAVITY_HAMMER.id(), SOUL_SCYTHE.id());
    }

    @Override
    public void initClient() {
        ClientRenderers.entityRenderer(GRAPPLE_HOOK, GrappleHookRenderer::new);
        ClientRenderers.entityRenderer(STORM_ARROW, StormArrowRenderer::new);
    }
}
