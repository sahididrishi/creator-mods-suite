package dev.riftal.creator.features.colossus;

import com.mojang.serialization.Codec;
import dev.riftal.creator.core.Feature;
import dev.riftal.creator.core.client.ClientRenderers;
import dev.riftal.creator.core.data.PlayerData;
import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.core.registry.EntityAttributes;
import dev.riftal.creator.core.registry.Registrar;
import dev.riftal.creator.core.registry.RegistryEntry;
import dev.riftal.creator.features.colossus.arena.Arena;
import dev.riftal.creator.features.colossus.arena.ArenaSavedData;
import dev.riftal.creator.features.colossus.client.AshBombRenderer;
import dev.riftal.creator.features.colossus.client.ColossusHud;
import dev.riftal.creator.features.colossus.client.ColossusRenderer;
import dev.riftal.creator.features.colossus.client.ColossusScreenShake;
import dev.riftal.creator.features.colossus.client.MinionRenderer;
import dev.riftal.creator.features.colossus.command.ColossusCommand;
import dev.riftal.creator.features.colossus.entity.AshBombEntity;
import dev.riftal.creator.features.colossus.entity.AshenColossusEntity;
import dev.riftal.creator.features.colossus.entity.AshenMinionEntity;
import dev.riftal.creator.features.colossus.net.ScreenShakePayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import org.jetbrains.annotations.Nullable;

import static dev.riftal.creator.Constants.LOG;

/**
 * Ashen Colossus - see {@code plans/02-ashen-colossus.md}.
 *
 * <p>A three-phase GeckoLib boss with a boss bar, minion waves, an arena ring and a loot table.
 *
 * <p>Everything this feature owns is declared in {@link #registerContent()}, which the loader only
 * calls when {@code colossus} is enabled in {@code config/creatormods.json}. Switch it off and the
 * entities, items, sounds, creative tab, payload, attachment, command and HUD layer are all simply
 * never created - the one mixin this feature ships re-checks the toggle at runtime, so a disabled
 * Colossus leaves the game completely vanilla.
 *
 * <p>This feature owns, and nothing else:
 * <ul>
 *   <li>{@code common/src/main/java/dev/riftal/creator/features/colossus/**}</li>
 *   <li>{@code common/src/main/resources/assets/creator_colossus/**} and {@code data/creator_colossus/**}</li>
 *   <li>{@code common/src/main/resources/creatormods-colossus.mixins.json}</li>
 *   <li>{@code common/src/test/java/dev/riftal/creator/features/colossus/**}</li>
 *   <li>{@code fabric|neoforge/src/main/java/dev/riftal/creator/features/colossus/**}</li>
 *   <li>{@code fabric/src/gametest/java/dev/riftal/creator/features/colossus/**}</li>
 * </ul>
 * See {@code CONTRACT.md} in the repo root.
 */
public final class ColossusFeature implements Feature {

    /** Feature id, resource namespace suffix and mixin config suffix. */
    public static final String ID = "colossus";

    /** Resource namespace owned by this feature. */
    public static final String NAMESPACE = "creator_colossus";

    // Assigned in registerContent(). Null while the feature is disabled - nothing that could be
    // reached with the feature off ever reads them.
    private static RegistryEntry<EntityType<AshenColossusEntity>> colossus;
    private static RegistryEntry<EntityType<AshenMinionEntity>> minion;
    private static RegistryEntry<EntityType<AshBombEntity>> ashBomb;

    private static RegistryEntry<Item> colossusEgg;
    private static RegistryEntry<Item> minionEgg;

    private static RegistryEntry<SoundEvent> roar;
    private static RegistryEntry<SoundEvent> swing;
    private static RegistryEntry<SoundEvent> slam;
    private static RegistryEntry<SoundEvent> step;
    private static RegistryEntry<SoundEvent> hurt;
    private static RegistryEntry<SoundEvent> death;
    private static RegistryEntry<SoundEvent> minionHurt;
    private static RegistryEntry<SoundEvent> minionDeath;

    /** The arena name this creator last worked with; the default for {@code /colossus spawn}. */
    private static PlayerData<String> lastArena;

    @Override
    public String id() {
        return ID;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void registerContent() {
        Registrar<SoundEvent> sounds = registrar(Registries.SOUND_EVENT);
        roar = sound(sounds, "colossus.roar");
        swing = sound(sounds, "colossus.swing");
        slam = sound(sounds, "colossus.slam");
        step = sound(sounds, "colossus.step");
        hurt = sound(sounds, "colossus.hurt");
        death = sound(sounds, "colossus.death");
        minionHurt = sound(sounds, "minion.hurt");
        minionDeath = sound(sounds, "minion.death");

        // Entity types are declared before items so the spawn eggs can resolve them: the loaders
        // flush ENTITY_TYPE before ITEM (vanilla registry order on NeoForge, declaration order on
        // Fabric), and a SpawnEggItem needs its EntityType at construction time.
        Registrar<EntityType<?>> entityTypes = registrar(Registries.ENTITY_TYPE);

        colossus = entityTypes.register("ashen_colossus", () -> EntityType.Builder
                .<AshenColossusEntity>of(AshenColossusEntity::new, MobCategory.MONSTER)
                .sized(2.6F, 5.2F)
                .eyeHeight(4.6F)
                .fireImmune()
                .clientTrackingRange(12)
                .updateInterval(2)
                .build("ashen_colossus"));

        minion = entityTypes.register("ashen_minion", () -> EntityType.Builder
                .<AshenMinionEntity>of(AshenMinionEntity::new, MobCategory.MONSTER)
                .sized(0.8F, 1.3F)
                .eyeHeight(1.05F)
                .fireImmune()
                .clientTrackingRange(8)
                .build("ashen_minion"));

        ashBomb = entityTypes.register("ash_bomb", () -> EntityType.Builder
                .<AshBombEntity>of(AshBombEntity::new, MobCategory.MISC)
                .sized(0.35F, 0.35F)
                .fireImmune()
                .clientTrackingRange(4)
                .updateInterval(10)
                .build("ash_bomb"));

        EntityAttributes.register(colossus, AshenColossusEntity::createAttributes);
        EntityAttributes.register(minion, AshenMinionEntity::createAttributes);

        Registrar<Item> items = registrar(Registries.ITEM);
        colossusEgg = items.register("ashen_colossus_spawn_egg",
                () -> new SpawnEggItem(colossus.get(), 0x2B2B2B, 0xFF5A1F, new Item.Properties()));
        minionEgg = items.register("ashen_minion_spawn_egg",
                () -> new SpawnEggItem(minion.get(), 0x3A3128, 0xFF8A3C, new Item.Properties()));

        Registrar<CreativeModeTab> tabs = registrar(Registries.CREATIVE_MODE_TAB);
        tabs.register(NAMESPACE, () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .title(Component.translatable("itemGroup." + NAMESPACE))
                .icon(() -> new ItemStack(colossusEgg.get()))
                .displayItems((params, output) -> {
                    output.accept(colossusEgg.get());
                    output.accept(minionEgg.get());
                })
                .build());

        lastArena = PlayerData.register(rl("last_arena"), Codec.STRING, () -> Arena.DEFAULT_NAME, true);

        // Registered on BOTH sides on purpose. The handler body only ever runs on a physical
        // client, but the payload *type* has to exist on a dedicated server too or the server
        // cannot encode the packet it is about to send.
        Payloads.registerS2C(ScreenShakePayload.TYPE, ScreenShakePayload.CODEC,
                ScreenShakePayload::handleOnClient);

        ColossusCommand.register();
    }

    @Override
    public void initCommon() {
        LOG.debug("[colossus] ready: 3 entity types, 8 sounds, /colossus registered");
    }

    @Override
    public void initClient() {
        ClientRenderers.entityRenderer(colossus, ColossusRenderer::new);
        ClientRenderers.entityRenderer(minion, MinionRenderer::new);
        ClientRenderers.entityRenderer(ashBomb, AshBombRenderer::new);
        ColossusHud.register(rl("ring"));
        // The only place in this feature that names the camera-shake class. The payload itself is
        // registered on both sides in registerContent() and must stay free of client types.
        ScreenShakePayload.installClientHandler(ColossusScreenShake::begin);
    }

    // ------------------------------------------------------------------ accessors

    public static RegistryEntry<EntityType<AshenColossusEntity>> colossus() {
        return colossus;
    }

    public static RegistryEntry<EntityType<AshenMinionEntity>> minion() {
        return minion;
    }

    public static RegistryEntry<EntityType<AshBombEntity>> ashBomb() {
        return ashBomb;
    }

    public static Item colossusSpawnEgg() {
        return colossusEgg.get();
    }

    public static Item minionSpawnEgg() {
        return minionEgg.get();
    }

    public static SoundEvent roarSound() {
        return roar.get();
    }

    public static SoundEvent swingSound() {
        return swing.get();
    }

    public static SoundEvent slamSound() {
        return slam.get();
    }

    public static SoundEvent stepSound() {
        return step.get();
    }

    public static SoundEvent hurtSound() {
        return hurt.get();
    }

    public static SoundEvent deathSound() {
        return death.get();
    }

    public static SoundEvent minionHurtSound() {
        return minionHurt.get();
    }

    public static SoundEvent minionDeathSound() {
        return minionDeath.get();
    }

    /** The arena name this player last set or spawned with. Never null. */
    public static String lastArenaOf(@Nullable ServerPlayer player) {
        if (player == null || lastArena == null) {
            return Arena.DEFAULT_NAME;
        }
        String stored = lastArena.get(player);
        return stored == null || stored.isBlank() ? Arena.DEFAULT_NAME : stored;
    }

    /** Remembers the arena a player is working with, so the next bare command reuses it. */
    public static void rememberArena(@Nullable ServerPlayer player, String name) {
        if (player == null || lastArena == null) {
            return;
        }
        lastArena.set(player, ArenaSavedData.normalise(name));
    }

    private RegistryEntry<SoundEvent> sound(Registrar<SoundEvent> sounds, String path) {
        return sounds.register(path, () -> SoundEvent.createVariableRangeEvent(rl(path)));
    }
}
