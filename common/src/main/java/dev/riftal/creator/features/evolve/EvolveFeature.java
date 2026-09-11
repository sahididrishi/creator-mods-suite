package dev.riftal.creator.features.evolve;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.core.Feature;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.core.data.PlayerData;
import dev.riftal.creator.core.registry.EntityAttributes;
import dev.riftal.creator.core.registry.Registrar;
import dev.riftal.creator.core.registry.RegistryEntry;
import dev.riftal.creator.features.evolve.client.EvolveClient;
import dev.riftal.creator.features.evolve.command.EvolveCommands;
import dev.riftal.creator.features.evolve.data.EvolutionData;
import dev.riftal.creator.features.evolve.entity.ApexBeast;
import dev.riftal.creator.features.evolve.event.EvolveServerHooks;
import dev.riftal.creator.features.evolve.net.EvolvePayloads;
import dev.riftal.creator.features.evolve.stage.Stages;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.Arrays;

/**
 * Evolve - see {@code plans/05-evolve.md}.
 *
 * <p>Player evolution stages that change size, stats and perks, with a transformation sequence.
 *
 * <p>This feature owns, and nothing else:
 * <ul>
 *   <li>{@code common/src/main/java/dev/riftal/creator/features/evolve/**}</li>
 *   <li>{@code common/src/main/resources/assets/creator_evolve/**} and {@code data/creator_evolve/**}</li>
 *   <li>{@code common/src/main/resources/creatormods-evolve.mixins.json}</li>
 *   <li>{@code common/src/test/java/dev/riftal/creator/features/evolve/**}</li>
 *   <li>{@code fabric|neoforge/src/main/java/dev/riftal/creator/features/evolve/**}</li>
 *   <li>{@code fabric/src/gametest/java/dev/riftal/creator/features/evolve/**}</li>
 * </ul>
 * See {@code CONTRACT.md} in the repo root.
 *
 * <p><b>Everything is declared in {@link #registerContent()}</b>, into static fields, rather than in
 * instance initialisers: {@code CreatorMods} constructs all eight feature objects whether or not
 * they are enabled, so field-initialiser registration would leak content from a feature the human
 * switched off. Read the entries back through the accessors below, never before
 * {@code registerContent()} has run - {@link #isReady()} says when that is.
 */
public final class EvolveFeature implements Feature {

    /** Feature id, resource namespace suffix and mixin config suffix. */
    public static final String ID = "evolve";

    /** Resource namespace owned by this feature. */
    public static final String NAMESPACE = "creator_evolve";

    /** Fabric-only listener glue, reached reflectively because {@code fabric.mod.json} is shared. */
    private static final String FABRIC_HOOKS =
            "dev.riftal.creator.features.evolve.event.EvolveFabricHooks";

    private static RegistryEntry<EntityType<ApexBeast>> apexBeast;
    private static RegistryEntry<SoundEvent> roarSmall;
    private static RegistryEntry<SoundEvent> roarApex;
    private static RegistryEntry<SoundEvent> evolveComplete;
    private static PlayerData<EvolutionData> evolutionData;

    /** {@code creator_evolve:<path>}, usable from static context. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    /**
     * True once {@link #registerContent()} has run, i.e. the feature is enabled and its content
     * exists. Every entry point - mixins, the heartbeat, commands - checks this first.
     */
    public static boolean isReady() {
        return evolutionData != null && CreatorMods.isEnabled(ID);
    }

    /** Per-player evolution state. Null until {@link #registerContent()} has run. */
    public static PlayerData<EvolutionData> data() {
        return evolutionData;
    }

    /** The Apex beast entity type. */
    public static RegistryEntry<EntityType<ApexBeast>> apexBeast() {
        return apexBeast;
    }

    /** Roar played when reaching stages 2-4. */
    public static SoundEvent roarSmall() {
        return roarSmall.get();
    }

    /** Roar played when reaching Apex. */
    public static SoundEvent roarApex() {
        return roarApex.get();
    }

    /** Chime played the instant a transformation completes. */
    public static SoundEvent evolveComplete() {
        return evolveComplete.get();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        Registrar<EntityType<?>> entityTypes = registrar(Registries.ENTITY_TYPE);
        Registrar<SoundEvent> sounds = registrar(Registries.SOUND_EVENT);

        apexBeast = entityTypes.register("apex_beast", () -> EntityType.Builder
                .<ApexBeast>of(ApexBeast::new, MobCategory.MISC)
                .sized(ApexBeast.MODEL_WIDTH, ApexBeast.MODEL_HEIGHT)
                .eyeHeight(ApexBeast.MODEL_HEIGHT * 0.9F)
                .clientTrackingRange(10)
                .build("apex_beast"));

        EntityAttributes.register(apexBeast, ApexBeast::createAttributes);

        roarSmall = sounds.register("evolve.roar_small",
                () -> SoundEvent.createVariableRangeEvent(id("evolve.roar_small")));
        roarApex = sounds.register("evolve.roar_apex",
                () -> SoundEvent.createVariableRangeEvent(id("evolve.roar_apex")));
        evolveComplete = sounds.register("evolve.complete",
                () -> SoundEvent.createVariableRangeEvent(id("evolve.complete")));

        evolutionData = PlayerData.register(id("evolution"), EvolutionData.CODEC,
                EvolutionData::initial, true);

        EvolvePayloads.registerAll();

        CommandHelper.register(dispatcher -> {
            EvolveCommands.register(dispatcher);
            // The dispatcher is rebuilt at server start and on /reload, which is the only
            // loader-neutral "a server exists now" signal core exposes. TickScheduler's queue is
            // cleared on server stop, so the heartbeat has to be re-armed here every time.
            EvolveServerHooks.armHeartbeat();
        });
    }

    @Override
    public void initCommon() {
        bootstrapFabricHooks();
        LOG.info("[evolve] {} stages armed, thresholds {}", Stages.ALL.size(),
                Arrays.toString(Stages.THRESHOLDS));
    }

    /**
     * Starts the Fabric start-tracking listener. NeoForge has no counterpart here: its twin is an
     * auto-scanned {@code @EventBusSubscriber} class in the {@code neoforge} module, which needs no
     * bootstrap at all. Fabric discovers listeners only through {@code fabric.mod.json}, a shared
     * file no feature may edit, so this is a single {@code Class.forName} into this feature's own
     * class in the {@code fabric} module - the pattern {@code PowersClient} already uses.
     *
     * <p>If it ever fails the feature still works; a player walking into tracking range just falls
     * back to the five-second roster pass for their first sync.
     */
    private static void bootstrapFabricHooks() {
        try {
            Class.forName(FABRIC_HOOKS).getMethod("init").invoke(null);
        } catch (ClassNotFoundException notFabric) {
            // NeoForge: the glue is an auto-scanned @EventBusSubscriber class instead.
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOG.warn("[evolve] Fabric start-tracking glue did not start; a player walking into "
                    + "range will see the right stage on the next roster pass instead", failure);
        }
    }

    @Override
    public void initClient() {
        EvolveClient.init();
    }

    @Override
    public void initServer() {
        // Nothing dedicated-server-only: the heartbeat and every hook are common code.
    }
}
