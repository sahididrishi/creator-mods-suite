package dev.riftal.creator.features.vault.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.features.vault.VaultFeature;
import dev.riftal.creator.features.vault.block.AltarState;
import dev.riftal.creator.features.vault.block.AltarStateMachine;
import dev.riftal.creator.features.vault.block.CursedAltarBlock;
import dev.riftal.creator.features.vault.block.entity.CursedAltarBlockEntity;
import dev.riftal.creator.features.vault.entity.VaultKeeper;
import dev.riftal.creator.features.vault.worldgen.VaultStructures;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /vault} - the recording controls for this feature. Every node needs op level 2 and every
 * reply goes through {@link CommandHelper}, so {@code /creator silent true} moves them to the
 * action bar instead of chat.
 *
 * <pre>
 * /vault key [count]        give yourself Vault Keys
 * /vault reset [radius]     re-seal the nearest altar's chests and re-arm it
 * /vault spawn_keeper       summon a Keeper here, bound to the nearest altar
 * /vault unseal             skip the fight and open the chests now
 * /vault tp                 teleport to the nearest generated Cursed Vault
 * /vault status             diagnostics for the nearest altar
 * </pre>
 */
public final class VaultCommands {

    /** Default search radius, in blocks, for "the nearest altar". */
    public static final int DEFAULT_ALTAR_RADIUS = 32;

    /** Hard cap on the altar search radius, so a typo cannot stall the server thread. */
    public static final int MAX_ALTAR_RADIUS = 128;

    /** {@code /vault tp} search radius, in chunks. */
    public static final int TP_CHUNK_RADIUS = 100;

    /** Registers the whole tree. Called from {@code VaultFeature#registerContent()}. */
    public static void register() {
        CommandHelper.register(dispatcher -> dispatcher.register(CommandHelper.op("vault")
                .then(CommandHelper.literal("key")
                        .executes(ctx -> giveKeys(ctx.getSource(), 1))
                        .then(CommandHelper.arg("count", IntegerArgumentType.integer(1, 16))
                                .executes(ctx -> giveKeys(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "count")))))
                .then(CommandHelper.literal("reset")
                        .executes(ctx -> reset(ctx.getSource(), DEFAULT_ALTAR_RADIUS))
                        .then(CommandHelper.arg("radius", IntegerArgumentType.integer(1, MAX_ALTAR_RADIUS))
                                .executes(ctx -> reset(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(CommandHelper.literal("spawn_keeper")
                        .executes(ctx -> spawnKeeper(ctx.getSource())))
                .then(CommandHelper.literal("unseal")
                        .executes(ctx -> unseal(ctx.getSource())))
                .then(CommandHelper.literal("tp")
                        .executes(ctx -> teleportToVault(ctx.getSource())))
                .then(CommandHelper.literal("status")
                        .executes(ctx -> status(ctx.getSource())))));
    }

    // ------------------------------------------------------------------- key

    private static int giveKeys(CommandSourceStack source, int count) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            return CommandHelper.error(source, Component.translatable("commands.creator_vault.player_only"));
        }
        player.getInventory().placeItemBackInInventory(new ItemStack(VaultFeature.VAULT_KEY.get(), count));
        return CommandHelper.success(source, Component.translatable("commands.creator_vault.key", count));
    }

    // ----------------------------------------------------------------- altar

    private static int reset(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        CursedAltarBlockEntity altar = nearestAltar(level, source.getPosition(), radius);
        if (altar == null) {
            return CommandHelper.error(source,
                    Component.translatable("commands.creator_vault.no_altar", radius));
        }
        int resealed = altar.reset(level);
        BlockPos pos = altar.getBlockPos();
        return CommandHelper.success(source, Component.translatable("commands.creator_vault.reset",
                pos.getX(), pos.getY(), pos.getZ(), resealed));
    }

    private static int unseal(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        CursedAltarBlockEntity altar = nearestAltar(level, source.getPosition(), DEFAULT_ALTAR_RADIUS);
        if (altar == null) {
            return CommandHelper.error(source,
                    Component.translatable("commands.creator_vault.no_altar", DEFAULT_ALTAR_RADIUS));
        }
        if (altar.state() == AltarState.SEALED) {
            // Nothing to open yet: prime it first so the same command always ends in open chests.
            altar.activate(null);
        }
        VaultKeeper keeper = altar.resolveKeeper(level);
        if (keeper != null) {
            keeper.discard();
        }
        int unsealed = altar.onKeeperDead(level);
        return CommandHelper.success(source,
                Component.translatable("commands.creator_vault.unseal", unsealed));
    }

    private static int spawnKeeper(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 where = source.getPosition();
        BlockPos pos = BlockPos.containing(where);
        CursedAltarBlockEntity altar = nearestAltar(level, where, DEFAULT_ALTAR_RADIUS);

        VaultKeeper keeper = VaultFeature.VAULT_KEEPER.get().spawn(level, pos, MobSpawnType.COMMAND);
        if (keeper == null) {
            return CommandHelper.error(source, Component.translatable("commands.creator_vault.spawn_failed"));
        }
        keeper.setPersistenceRequired();
        if (altar == null || !altar.adoptKeeper(level, keeper)) {
            return CommandHelper.success(source,
                    Component.translatable("commands.creator_vault.spawned_unbound"));
        }
        BlockPos altarPos = altar.getBlockPos();
        return CommandHelper.success(source, Component.translatable("commands.creator_vault.spawned_bound",
                altarPos.getX(), altarPos.getY(), altarPos.getZ()));
    }

    private static int status(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        CursedAltarBlockEntity altar = nearestAltar(level, source.getPosition(), DEFAULT_ALTAR_RADIUS);
        if (altar == null) {
            return CommandHelper.error(source,
                    Component.translatable("commands.creator_vault.no_altar", DEFAULT_ALTAR_RADIUS));
        }
        BlockPos pos = altar.getBlockPos();
        VaultKeeper keeper = altar.resolveKeeper(level);
        int missingLeft = Math.max(0, AltarStateMachine.KEEPER_MISSING_LIMIT - altar.missingKeeperTicks());
        CommandHelper.feedback(source, Component.translatable("commands.creator_vault.status.header",
                pos.getX(), pos.getY(), pos.getZ()));
        CommandHelper.feedback(source, Component.translatable("commands.creator_vault.status.state",
                altar.state().getSerializedName(), altar.chargeTicks(), AltarStateMachine.CHARGE_TICKS));
        CommandHelper.feedback(source, Component.translatable("commands.creator_vault.status.keeper",
                keeper != null && keeper.isAlive()
                        ? Component.translatable("commands.creator_vault.status.keeper_alive")
                        : Component.translatable("commands.creator_vault.status.keeper_gone", missingLeft)));
        ServerPlayer player = source.getPlayer();
        return CommandHelper.feedback(source, Component.translatable("commands.creator_vault.status.chests",
                altar.sealedChestCount(),
                player == null ? 0 : VaultFeature.KEYS_USED.get(player)));
    }

    // -------------------------------------------------------------------- tp

    private static int teleportToVault(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            return CommandHelper.error(source, Component.translatable("commands.creator_vault.player_only"));
        }
        ServerLevel level = source.getLevel();
        BlockPos found = level.findNearestMapStructure(VaultStructures.CURSED_VAULT_TAG,
                player.blockPosition(), TP_CHUNK_RADIUS, false);
        if (found == null) {
            return CommandHelper.error(source, Component.translatable("commands.creator_vault.no_vault",
                    TP_CHUNK_RADIUS * 16));
        }
        BlockPos target = findVaultEntry(level, found);
        player.teleportTo(level, target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        return CommandHelper.success(source, Component.translatable("commands.creator_vault.tp",
                target.getX(), target.getY(), target.getZ()));
    }

    /**
     * Turns the {@code /locate}-style position (which always has y = 0) into somewhere a player can
     * actually stand. Prefers the Cursed Altar itself, then any air pocket with a floor, then the
     * nominal vault floor.
     */
    private static BlockPos findVaultEntry(ServerLevel level, BlockPos located) {
        int centreX = located.getX() + 8;
        int centreZ = located.getZ() + 8;
        BlockPos fallback = new BlockPos(centreX, VaultStructures.VAULT_START_Y + 2, centreZ);
        BlockPos firstOpen = null;
        for (int y = VaultStructures.VAULT_START_Y + 10; y >= VaultStructures.VAULT_START_Y - 4; y--) {
            for (int dx = -16; dx <= 16; dx++) {
                for (int dz = -16; dz <= 16; dz++) {
                    BlockPos probe = new BlockPos(centreX + dx, y, centreZ + dz);
                    if (level.getBlockState(probe).getBlock() instanceof CursedAltarBlock) {
                        return probe.above();
                    }
                    if (firstOpen == null
                            && level.getBlockState(probe).isAir()
                            && level.getBlockState(probe.above()).isAir()
                            && !level.getBlockState(probe.below()).isAir()) {
                        firstOpen = probe;
                    }
                }
            }
        }
        return firstOpen != null ? firstOpen : fallback;
    }

    // ----------------------------------------------------------------- lookup

    /**
     * Nearest Cursed Altar block entity to {@code centre}.
     *
     * <p>Walks the loaded chunks' block-entity maps rather than scanning blocks, so a 128-block
     * radius costs a few hundred map lookups instead of sixteen million {@code getBlockState}
     * calls.
     */
    @Nullable
    public static CursedAltarBlockEntity nearestAltar(ServerLevel level, Vec3 centre, int radius) {
        int clamped = Math.max(1, Math.min(radius, MAX_ALTAR_RADIUS));
        int centreChunkX = SectionPos.blockToSectionCoord(BlockPos.containing(centre).getX());
        int centreChunkZ = SectionPos.blockToSectionCoord(BlockPos.containing(centre).getZ());
        int chunkRadius = (clamped >> 4) + 1;
        double bestDistance = (double) clamped * clamped;
        CursedAltarBlockEntity best = null;

        for (int cx = centreChunkX - chunkRadius; cx <= centreChunkX + chunkRadius; cx++) {
            for (int cz = centreChunkZ - chunkRadius; cz <= centreChunkZ + chunkRadius; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    if (!(entry.getValue() instanceof CursedAltarBlockEntity altar)) {
                        continue;
                    }
                    double distance = Vec3.atCenterOf(entry.getKey()).distanceToSqr(centre);
                    if (distance <= bestDistance) {
                        bestDistance = distance;
                        best = altar;
                    }
                }
            }
        }
        return best;
    }

    private VaultCommands() {
    }
}
