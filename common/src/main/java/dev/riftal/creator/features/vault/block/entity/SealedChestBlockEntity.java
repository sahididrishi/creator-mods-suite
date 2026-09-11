package dev.riftal.creator.features.vault.block.entity;

import dev.riftal.creator.features.vault.VaultFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Holds the loot table a Sealed Chest hands to the vanilla chest it becomes.
 *
 * <p>Deliberately not a container: a Sealed Chest has no inventory at all, so nothing can be piped
 * in or out of it before the Keeper dies. Structure NBT can override the table per chest with
 * {@code {LootTable: "creator_vault:chests/cursed_vault", LootSeed: 0L}}; a seed of 0 means "roll
 * fresh loot the first time a player opens it", which is how vanilla dungeon chests behave.
 */
public class SealedChestBlockEntity extends BlockEntity {

    private ResourceKey<LootTable> lootTable = VaultFeature.CHEST_LOOT_TABLE;
    private long lootTableSeed;

    public SealedChestBlockEntity(BlockPos pos, BlockState blockState) {
        super(VaultFeature.SEALED_CHEST_BE.get(), pos, blockState);
    }

    public ResourceKey<LootTable> lootTable() {
        return this.lootTable;
    }

    public void setLootTable(ResourceKey<LootTable> lootTable) {
        this.lootTable = lootTable;
        setChanged();
    }

    public long lootTableSeed() {
        return this.lootTableSeed;
    }

    public void setLootTableSeed(long lootTableSeed) {
        this.lootTableSeed = lootTableSeed;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("LootTable", this.lootTable.location().toString());
        tag.putLong("LootSeed", this.lootTableSeed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("LootTable")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("LootTable"));
            this.lootTable = id == null
                    ? VaultFeature.CHEST_LOOT_TABLE
                    : ResourceKey.create(Registries.LOOT_TABLE, id);
        }
        this.lootTableSeed = tag.getLong("LootSeed");
    }
}
