package dev.riftal.creator.features.vault.block;

import static dev.riftal.creator.Constants.LOG;

import com.mojang.serialization.MapCodec;
import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.vault.VaultFeature;
import dev.riftal.creator.features.vault.block.entity.SealedChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * An obsidian-crusted, chained chest that cannot be opened, mined or pushed until the Vault Keeper
 * bound to the nearby Cursed Altar dies.
 *
 * <p>Unsealing does not open a GUI of its own - it swaps the block for a real vanilla chest with a
 * loot table attached, so the loot rolls the first time a player opens it, exactly like a dungeon
 * chest, and every mod that understands chests understands this one.
 */
public class SealedChestBlock extends BaseEntityBlock {

    public static final MapCodec<SealedChestBlock> CODEC = simpleCodec(SealedChestBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 15.0D, 15.0D);

    public SealedChestBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SealedChestBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.7F, 0.6F);
        player.displayClientMessage(Component.translatable("block.creator_vault.sealed_chest.locked"), true);
        return InteractionResult.CONSUME;
    }

    /**
     * Replaces the Sealed Chest with a vanilla chest carrying this feature's loot table.
     *
     * <p>The loot table must be set on the <em>new</em> block entity: the sealed one is discarded
     * by {@code setBlock}.
     *
     * @param lootSeed the altar's roll seed; 0 means "roll fresh loot when a player first opens it"
     * @return true when a Sealed Chest was actually converted
     */
    public static boolean unseal(ServerLevel level, BlockPos pos, long lootSeed) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SealedChestBlock)) {
            return false;
        }
        Direction facing = state.getValue(FACING);
        ResourceKey<LootTable> lootTable = VaultFeature.CHEST_LOOT_TABLE;
        long seed = lootSeed;
        if (level.getBlockEntity(pos) instanceof SealedChestBlockEntity sealed) {
            lootTable = sealed.lootTable();
            if (sealed.lootTableSeed() != 0L) {
                seed = sealed.lootTableSeed();
            }
        }

        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing),
                Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(lootTable);
            chest.setLootTableSeed(seed);
            chest.setChanged();
        } else {
            LOG.warn("[vault] unsealed chest at {} did not produce a ChestBlockEntity", pos);
        }

        Vec3 centre = Vec3.atCenterOf(pos);
        Fx.particles(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()),
                centre, 40, 0.45D, 0.05D);
        Fx.sound(level, centre, VaultFeature.CHEST_UNSEAL.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /**
     * Puts a Sealed Chest back at {@code pos} carrying this feature's default loot table.
     *
     * @see #reseal(ServerLevel, BlockPos, Direction, ResourceKey, long)
     */
    public static void reseal(ServerLevel level, BlockPos pos, Direction facing) {
        reseal(level, pos, facing, VaultFeature.CHEST_LOOT_TABLE, 0L);
    }

    /**
     * Puts a Sealed Chest back at {@code pos}, discarding whatever was there. Used by
     * {@code /vault reset} through the altar.
     *
     * <p>{@code setBlock} creates a brand new {@link SealedChestBlockEntity} with the field
     * defaults, so the per-chest loot override {@link #unseal} reads has to be written back onto
     * it explicitly - otherwise a chest dressed with a custom table in the structure NBT silently
     * reverts to the default one after the first reset.
     *
     * @param lootTable the table the re-sealed chest will hand on when it is next unsealed
     * @param lootSeed  that chest's own seed; 0 means "roll fresh loot on first open"
     */
    public static void reseal(ServerLevel level, BlockPos pos, Direction facing,
                              ResourceKey<LootTable> lootTable, long lootSeed) {
        level.setBlock(pos, VaultFeature.SEALED_CHEST.get().defaultBlockState().setValue(FACING, facing),
                Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof SealedChestBlockEntity sealed) {
            sealed.setLootTable(lootTable);
            sealed.setLootTableSeed(lootSeed);
        } else {
            LOG.warn("[vault] re-sealed chest at {} did not produce a SealedChestBlockEntity", pos);
        }
    }

    /**
     * Unseals every Sealed Chest in a cube of {@code radius} around {@code centre}, ignoring
     * altars entirely.
     *
     * <p>The recovery path for the one accident that is otherwise unfixable: an altar mined in
     * creative while dressing a set takes its chest bindings with it, and a Sealed Chest is
     * unbreakable, unopenable, unpushable and not a container, so nothing else in this feature can
     * ever get at it again. {@code /vault unseal} falls back to this when there is no altar.
     *
     * <p>Only walks loaded chunks' block-entity maps - never {@code getBlockState} over a cube,
     * which would generate terrain on the server thread.
     *
     * @return how many chests were opened
     */
    public static int unsealAround(ServerLevel level, BlockPos centre, int radius, long lootSeed) {
        double radiusSqr = (double) radius * radius;
        int minChunkX = SectionPos.blockToSectionCoord(centre.getX() - radius);
        int maxChunkX = SectionPos.blockToSectionCoord(centre.getX() + radius);
        int minChunkZ = SectionPos.blockToSectionCoord(centre.getZ() - radius);
        int maxChunkZ = SectionPos.blockToSectionCoord(centre.getZ() + radius);

        List<BlockPos> found = new ArrayList<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!level.getChunkSource().hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    if (!(entry.getValue() instanceof SealedChestBlockEntity)) {
                        continue;
                    }
                    if (entry.getKey().distSqr(centre) <= radiusSqr) {
                        found.add(entry.getKey().immutable());
                    }
                }
            }
        }

        int unsealed = 0;
        for (BlockPos pos : found) {
            if (unseal(level, pos, lootSeed)) {
                unsealed++;
            }
        }
        return unsealed;
    }
}
