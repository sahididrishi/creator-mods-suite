package dev.riftal.creator.features.vault;

import static dev.riftal.creator.Constants.LOG;

import com.mojang.serialization.Codec;
import dev.riftal.creator.core.Feature;
import dev.riftal.creator.core.data.PlayerData;
import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.core.registry.EntityAttributes;
import dev.riftal.creator.core.registry.Registrar;
import dev.riftal.creator.core.registry.RegistryEntry;
import dev.riftal.creator.features.vault.block.AltarState;
import dev.riftal.creator.features.vault.block.AltarStateMachine;
import dev.riftal.creator.features.vault.block.CursedAltarBlock;
import dev.riftal.creator.features.vault.block.SealedChestBlock;
import dev.riftal.creator.features.vault.block.entity.CursedAltarBlockEntity;
import dev.riftal.creator.features.vault.block.entity.SealedChestBlockEntity;
import dev.riftal.creator.features.vault.block.entity.VaultBlockEntityType;
import dev.riftal.creator.features.vault.client.VaultClientSetup;
import dev.riftal.creator.features.vault.command.VaultCommands;
import dev.riftal.creator.features.vault.entity.VaultKeeper;
import dev.riftal.creator.features.vault.item.VaultKeyItem;
import dev.riftal.creator.features.vault.net.VaultHudState;
import dev.riftal.creator.features.vault.net.VaultStatusPayload;
import dev.riftal.creator.features.vault.worldgen.VaultStructures;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Cursed Vault - see {@code plans/08-cursed-vault.md}.
 *
 * <p>A generated jigsaw structure with a keyed altar, a keeper fight and a sealed reward chest.
 * The whole loop is: {@code /locate structure creator_vault:cursed_vault} -&gt; {@code /vault tp} -&gt;
 * right-click the Cursed Altar with a Vault Key -&gt; the Vault Keeper bursts out -&gt; kill it -&gt; the
 * Sealed Chests crack into vanilla loot chests -&gt; {@code /vault reset} puts it all back for the
 * next take.
 *
 * <p>This feature owns, and nothing else:
 * <ul>
 *   <li>{@code common/src/main/java/dev/riftal/creator/features/vault/**}</li>
 *   <li>{@code common/src/main/resources/assets/creator_vault/**} and {@code data/creator_vault/**}</li>
 *   <li>{@code common/src/main/resources/creatormods-vault.mixins.json}</li>
 *   <li>{@code common/src/test/java/dev/riftal/creator/features/vault/**}</li>
 *   <li>{@code fabric|neoforge/src/main/java/dev/riftal/creator/features/vault/**}</li>
 *   <li>{@code fabric/src/gametest/java/dev/riftal/creator/features/vault/**}</li>
 * </ul>
 * See {@code CONTRACT.md} in the repo root.
 */
public final class VaultFeature implements Feature {

    /** Feature id, resource namespace suffix and mixin config suffix. */
    public static final String ID = "vault";

    /** Resource namespace owned by this feature. */
    public static final String NAMESPACE = "creator_vault";

    /** {@code TickScheduler} owner tag for every task this feature schedules. */
    public static final ResourceLocation SCHED_ALTAR =
            ResourceLocation.fromNamespaceAndPath(NAMESPACE, "altar");

    /** Loot the Sealed Chests hand to the vanilla chests they become. */
    public static final ResourceKey<LootTable> CHEST_LOOT_TABLE = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(NAMESPACE, "chests/cursed_vault"));

    /** Loot for the trap-room chest, which always contains one Vault Key. */
    public static final ResourceKey<LootTable> TRAP_LOOT_TABLE = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(NAMESPACE, "chests/cursed_vault_trap"));

    // ---------------------------------------------------------------- content
    // Assigned in registerContent(), never in a static or instance initialiser: CreatorMods builds
    // all eight Feature objects up front, so anything registered from an initialiser would also be
    // registered for a feature the config has switched off.

    /** {@code creator_vault:cursed_altar}. */
    public static RegistryEntry<Block> CURSED_ALTAR;
    /** {@code creator_vault:sealed_chest}. */
    public static RegistryEntry<Block> SEALED_CHEST;

    /** Block entity behind {@link #CURSED_ALTAR}. */
    public static RegistryEntry<BlockEntityType<CursedAltarBlockEntity>> CURSED_ALTAR_BE;
    /** Block entity behind {@link #SEALED_CHEST}. */
    public static RegistryEntry<BlockEntityType<SealedChestBlockEntity>> SEALED_CHEST_BE;

    /** {@code creator_vault:vault_key}. */
    public static RegistryEntry<Item> VAULT_KEY;
    /** Block item for the altar. */
    public static RegistryEntry<Item> CURSED_ALTAR_ITEM;
    /** Block item for the sealed chest. */
    public static RegistryEntry<Item> SEALED_CHEST_ITEM;
    /** Spawn egg for the Keeper, so the mini-boss can be dropped in without a command. */
    public static RegistryEntry<Item> VAULT_KEEPER_SPAWN_EGG;

    /** {@code creator_vault:vault_keeper}. */
    public static RegistryEntry<EntityType<VaultKeeper>> VAULT_KEEPER;

    /** {@code creator_vault:altar.activate}. */
    public static RegistryEntry<SoundEvent> ALTAR_ACTIVATE;
    /** {@code creator_vault:altar.unseal}. */
    public static RegistryEntry<SoundEvent> ALTAR_UNSEAL;
    /** {@code creator_vault:altar.reset}. */
    public static RegistryEntry<SoundEvent> ALTAR_RESET;
    /** {@code creator_vault:chest.unseal}. */
    public static RegistryEntry<SoundEvent> CHEST_UNSEAL;
    /** {@code creator_vault:keeper.summon}. */
    public static RegistryEntry<SoundEvent> KEEPER_SUMMON;
    /** {@code creator_vault:keeper.idle}. */
    public static RegistryEntry<SoundEvent> KEEPER_IDLE;
    /** {@code creator_vault:keeper.hurt}. */
    public static RegistryEntry<SoundEvent> KEEPER_HURT;
    /** {@code creator_vault:keeper.death}. */
    public static RegistryEntry<SoundEvent> KEEPER_DEATH;

    /** The feature's single creative tab. */
    public static RegistryEntry<CreativeModeTab> TAB;

    /** How many Vault Keys this player has fed to altars. Survives death; shown on the HUD. */
    public static PlayerData<Integer> KEYS_USED;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        // Declaration order matters on Fabric, where registrars flush in the order they were
        // created: blocks and entity types must exist before the items that reference them, and
        // the creative tab last of all.
        Registrar<SoundEvent> sounds = registrar(Registries.SOUND_EVENT);
        Registrar<Block> blocks = registrar(Registries.BLOCK);
        Registrar<EntityType<?>> entityTypes = registrar(Registries.ENTITY_TYPE);
        Registrar<Item> items = registrar(Registries.ITEM);
        Registrar<BlockEntityType<?>> blockEntityTypes = registrar(Registries.BLOCK_ENTITY_TYPE);
        Registrar<CreativeModeTab> tabs = registrar(Registries.CREATIVE_MODE_TAB);

        ALTAR_ACTIVATE = sound(sounds, "altar.activate");
        ALTAR_UNSEAL = sound(sounds, "altar.unseal");
        ALTAR_RESET = sound(sounds, "altar.reset");
        CHEST_UNSEAL = sound(sounds, "chest.unseal");
        KEEPER_SUMMON = sound(sounds, "keeper.summon");
        KEEPER_IDLE = sound(sounds, "keeper.idle");
        KEEPER_HURT = sound(sounds, "keeper.hurt");
        KEEPER_DEATH = sound(sounds, "keeper.death");

        CURSED_ALTAR = blocks.register("cursed_altar", () -> new CursedAltarBlock(
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .strength(4.0F, 1200.0F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.DEEPSLATE_BRICKS)
                        .lightLevel(state -> state.getValue(CursedAltarBlock.STATE) == AltarState.SEALED ? 3 : 10)
                        .noOcclusion()));

        SEALED_CHEST = blocks.register("sealed_chest", () -> new SealedChestBlock(
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .strength(-1.0F, 3600000.0F)
                        .sound(SoundType.DEEPSLATE_BRICKS)
                        .pushReaction(PushReaction.BLOCK)
                        .noOcclusion()));

        VAULT_KEEPER = entityTypes.register("vault_keeper", () -> EntityType.Builder
                .<VaultKeeper>of(VaultKeeper::new, MobCategory.MONSTER)
                .sized(0.8F, 2.3F)
                .eyeHeight(2.0F)
                .clientTrackingRange(10)
                .fireImmune()
                .build("vault_keeper"));

        VAULT_KEY = items.register("vault_key", () -> new VaultKeyItem(
                new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
        CURSED_ALTAR_ITEM = items.register("cursed_altar",
                () -> new BlockItem(CURSED_ALTAR.get(), new Item.Properties()));
        SEALED_CHEST_ITEM = items.register("sealed_chest",
                () -> new BlockItem(SEALED_CHEST.get(), new Item.Properties()));
        VAULT_KEEPER_SPAWN_EGG = items.register("vault_keeper_spawn_egg",
                () -> new SpawnEggItem(VAULT_KEEPER.get(), 0x1B1226, 0x9B4DFF, new Item.Properties()));

        // NOT BlockEntityType.Builder: its factory parameter type
        // (BlockEntityType.BlockEntitySupplier) is package-private in vanilla 1.21.1, so no lambda
        // or method reference in this package can target it. See VaultBlockEntityType's javadoc.
        CURSED_ALTAR_BE = blockEntityTypes.register("cursed_altar",
                () -> VaultBlockEntityType.<CursedAltarBlockEntity>of(
                        CursedAltarBlockEntity::new, CURSED_ALTAR.get()));
        SEALED_CHEST_BE = blockEntityTypes.register("sealed_chest",
                () -> VaultBlockEntityType.<SealedChestBlockEntity>of(
                        SealedChestBlockEntity::new, SEALED_CHEST.get()));

        TAB = tabs.register(NAMESPACE, () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .title(Component.translatable("itemGroup." + NAMESPACE))
                .icon(() -> new ItemStack(VAULT_KEY.get()))
                .displayItems((parameters, output) -> {
                    output.accept(VAULT_KEY.get());
                    output.accept(CURSED_ALTAR_ITEM.get());
                    output.accept(SEALED_CHEST_ITEM.get());
                    output.accept(VAULT_KEEPER_SPAWN_EGG.get());
                })
                .build());

        EntityAttributes.register(VAULT_KEEPER, VaultKeeper::createAttributes);

        KEYS_USED = PlayerData.register(rl("keys_used"), Codec.INT, () -> 0, true);

        // Registered on BOTH sides, not only in initClient(): Fabric's PayloadTypeRegistry entry
        // for an S2C payload has to exist on a dedicated server too, or sending it throws. The
        // handler target lives in a class with no client types in it, so nothing client-only is
        // loaded by this line.
        Payloads.registerS2C(VaultStatusPayload.TYPE, VaultStatusPayload.CODEC, VaultHudState::accept);

        VaultCommands.register();
    }

    @Override
    public void initCommon() {
        LOG.info("[vault] ready: structure {} at y={}, altar charge {} ticks",
                VaultStructures.CURSED_VAULT_ID,
                VaultStructures.VAULT_START_Y,
                AltarStateMachine.CHARGE_TICKS);
    }

    @Override
    public void initClient() {
        // Everything client-only is behind this one class so a dedicated server never loads it.
        VaultClientSetup.init();
    }

    @Override
    public void initServer() {
        // Nothing dedicated-server-specific: the altar ticker, the commands and the Keeper's boss
        // bar are already server-side, and core clears the scheduler on server stop.
        LOG.debug("[vault] dedicated server init, scheduler tag {}", SCHED_ALTAR);
    }

    private RegistryEntry<SoundEvent> sound(Registrar<SoundEvent> sounds, String path) {
        return sounds.register(path,
                () -> SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(NAMESPACE, path)));
    }
}
