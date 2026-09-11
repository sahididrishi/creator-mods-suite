package dev.riftal.creator.features.events;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.core.Feature;
import dev.riftal.creator.core.client.ClientRenderers;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.core.hud.HudLayers;
import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.core.registry.Registrar;
import dev.riftal.creator.core.registry.RegistryEntry;
import dev.riftal.creator.features.events.api.EventRegistry;
import dev.riftal.creator.features.events.block.LuckyRainBlock;
import dev.riftal.creator.features.events.client.ClientEventState;
import dev.riftal.creator.features.events.client.EventHud;
import dev.riftal.creator.features.events.client.MeteorRenderer;
import dev.riftal.creator.features.events.entity.MeteorEntity;
import dev.riftal.creator.features.events.events.BloodMoonEvent;
import dev.riftal.creator.features.events.events.LuckyRainEvent;
import dev.riftal.creator.features.events.events.MeteorEvent;
import dev.riftal.creator.features.events.events.SiegeEvent;
import dev.riftal.creator.features.events.events.VoidRiseEvent;
import dev.riftal.creator.features.events.command.EventCommand;
import dev.riftal.creator.features.events.hooks.EventHooks;
import dev.riftal.creator.features.events.lucky.LuckyOutcome;
import dev.riftal.creator.features.events.lucky.LuckyOutcomes;
import dev.riftal.creator.features.events.net.EventStatePayload;
import dev.riftal.creator.features.events.siege.SiegeWave;
import dev.riftal.creator.features.events.siege.SiegeWaves;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Event Director - see {@code plans/06-event-director.md}.
 *
 * <p>Timed world events - blood moon, meteor shower, siege, lucky rain, void rise - that survive a relog.
 *
 * <p>This feature owns, and nothing else:
 * <ul>
 *   <li>{@code common/src/main/java/dev/riftal/creator/features/events/**}</li>
 *   <li>{@code common/src/main/resources/assets/creator_events/**} and {@code data/creator_events/**}</li>
 *   <li>{@code common/src/main/resources/creatormods-events.mixins.json}</li>
 *   <li>{@code common/src/test/java/dev/riftal/creator/features/events/**}</li>
 *   <li>{@code fabric|neoforge/src/main/java/dev/riftal/creator/features/events/**}</li>
 *   <li>{@code fabric/src/gametest/java/dev/riftal/creator/features/events/**}</li>
 * </ul>
 * See {@code CONTRACT.md} in the repo root.
 */
public final class EventsFeature implements Feature {

    /** Feature id, resource namespace suffix and mixin config suffix. */
    public static final String ID = "events";

    /** Resource namespace owned by this feature. */
    public static final String NAMESPACE = "creator_events";

    private static RegistryEntry<LuckyRainBlock> luckyRain;
    private static RegistryEntry<Item> luckyRainItem;
    private static RegistryEntry<EntityType<MeteorEntity>> meteor;
    private static RegistryEntry<SoundEvent> bloodmoonDrone;
    private static RegistryEntry<SoundEvent> meteorWhistle;
    private static RegistryEntry<SoundEvent> meteorImpact;
    private static RegistryEntry<SoundEvent> siegeHorn;
    private static RegistryEntry<SoundEvent> luckyPop;
    private static RegistryEntry<SoundEvent> voidHum;
    private static RegistryEntry<CreativeModeTab> tab;

    private static List<LuckyOutcome> luckyOutcomes;
    private static List<SiegeWave> siegeWaves;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        Registrar<Block> blocks = registrar(Registries.BLOCK);
        Registrar<Item> items = registrar(Registries.ITEM);
        Registrar<EntityType<?>> entityTypes = registrar(Registries.ENTITY_TYPE);
        Registrar<SoundEvent> sounds = registrar(Registries.SOUND_EVENT);
        Registrar<CreativeModeTab> tabs = registrar(Registries.CREATIVE_MODE_TAB);

        luckyRain = blocks.register("lucky_rain", () -> new LuckyRainBlock(
                BlockBehaviour.Properties.of()
                        .strength(0.5F)
                        .lightLevel(state -> 8)
                        .sound(SoundType.METAL)));
        luckyRainItem = items.register("lucky_rain",
                () -> new BlockItem(luckyRain.get(), new Item.Properties()));

        meteor = entityTypes.register("meteor", () -> EntityType.Builder
                .<MeteorEntity>of(MeteorEntity::new, MobCategory.MISC)
                .sized(1.5F, 1.5F)
                .clientTrackingRange(10)
                .updateInterval(2)
                .fireImmune()
                .build("meteor"));

        bloodmoonDrone = sounds.register("bloodmoon.drone", () -> variableRange("bloodmoon.drone"));
        meteorWhistle = sounds.register("meteor.whistle", () -> variableRange("meteor.whistle"));
        meteorImpact = sounds.register("meteor.impact", () -> variableRange("meteor.impact"));
        siegeHorn = sounds.register("siege.horn", () -> variableRange("siege.horn"));
        luckyPop = sounds.register("lucky.pop", () -> variableRange("lucky.pop"));
        voidHum = sounds.register("void.hum", () -> variableRange("void.hum"));

        tab = tabs.register(NAMESPACE, () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .title(Component.translatable("itemGroup." + NAMESPACE))
                .icon(() -> new ItemStack(luckyRainItem.get()))
                .displayItems((params, output) -> output.accept(luckyRainItem.get()))
                .build());

        EventRegistry.register("bloodmoon", BloodMoonEvent::new);
        EventRegistry.register("meteor", MeteorEvent::new);
        EventRegistry.register("siege", SiegeEvent::new);
        EventRegistry.register("luckyrain", LuckyRainEvent::new);
        EventRegistry.register("voidrise", VoidRiseEvent::new);

        // Registered on BOTH sides on purpose: the loader helpers register the payload type in the
        // same call as the receiver, and a dedicated server that never ran initClient() would then
        // be unable to send it. ClientEventState deliberately touches no net.minecraft.client type,
        // so naming it here is safe on a dedicated server.
        Payloads.registerS2C(EventStatePayload.TYPE, EventStatePayload.CODEC, ClientEventState::accept);

        CommandHelper.register(dispatcher -> {
            dispatcher.register(EventCommand.build());
            // Both loaders rebuild the dispatcher on server start, which is the one hook a feature
            // gets before the first tick: arm the director's driver from here.
            EventManager.ensureDriver();
        });
    }

    @Override
    public void initCommon() {
        EventHooks.reset();
        LOG.info("[events] {} event(s) available: {}", EventRegistry.ids().size(), EventRegistry.ids());
    }

    @Override
    public void initClient() {
        HudLayers.register(rl("event_hud"), EventHud::render);
        ClientRenderers.entityRenderer(meteor, MeteorRenderer::new);
    }

    /** The falling "?" block. */
    public static LuckyRainBlock luckyRainBlock() {
        return luckyRain.get();
    }

    /** The meteor entity type. */
    public static EntityType<MeteorEntity> meteorType() {
        return meteor.get();
    }

    public static SoundEvent bloodmoonDrone() {
        return bloodmoonDrone.get();
    }

    public static SoundEvent meteorWhistle() {
        return meteorWhistle.get();
    }

    public static SoundEvent meteorImpact() {
        return meteorImpact.get();
    }

    public static SoundEvent siegeHorn() {
        return siegeHorn.get();
    }

    public static SoundEvent luckyPop() {
        return luckyPop.get();
    }

    public static SoundEvent voidHum() {
        return voidHum.get();
    }

    /**
     * The lucky-rain drop table, from {@code data/creator_events/lucky_outcomes/default.json} if a
     * data pack ships one, otherwise the built-in table. Cached until {@code /event reload}.
     */
    public static List<LuckyOutcome> luckyOutcomes(MinecraftServer server) {
        if (luckyOutcomes == null) {
            luckyOutcomes = read(server, "lucky_outcomes/default.json")
                    .map(LuckyOutcomes::parse)
                    .orElseGet(LuckyOutcomes::builtIn);
        }
        return luckyOutcomes;
    }

    /**
     * The siege wave table, from {@code data/creator_events/siege_waves/default.json} if present.
     * Cached until {@code /event reload}.
     */
    public static List<SiegeWave> siegeWaves(MinecraftServer server) {
        if (siegeWaves == null) {
            siegeWaves = read(server, "siege_waves/default.json")
                    .map(SiegeWaves::parse)
                    .orElseGet(SiegeWaves::defaults);
        }
        return siegeWaves;
    }

    /**
     * Drops both caches without re-reading them. Called when the director binds a new server, so a
     * single-player session that leaves world A and opens world B does not keep A's data pack
     * tables - the caches are static and would otherwise outlive the server that filled them.
     */
    public static void clearDataCaches() {
        luckyOutcomes = null;
        siegeWaves = null;
    }

    /** {@code /event reload}: drops both caches so the next read picks the data pack back up. */
    public static void reloadData(MinecraftServer server) {
        luckyOutcomes = null;
        siegeWaves = null;
        int outcomes = luckyOutcomes(server).size();
        int waves = siegeWaves(server).size();
        LOG.info("[events] reloaded {} lucky outcome(s) and {} siege wave(s)", outcomes, waves);
    }

    private static Optional<String> read(MinecraftServer server, String path) {
        if (server == null) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
        Optional<Resource> resource = server.getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
            return Optional.of(text.toString());
        } catch (IOException e) {
            LOG.error("[events] could not read {}", id, e);
            return Optional.empty();
        }
    }

    private static SoundEvent variableRange(String path) {
        return SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(NAMESPACE, path));
    }
}
