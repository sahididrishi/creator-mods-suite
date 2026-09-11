package dev.riftal.creator.features.vault.command;

import static dev.riftal.creator.Constants.LOG;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.features.vault.VaultFeature;
import dev.riftal.creator.features.vault.block.AltarState;
import dev.riftal.creator.features.vault.block.AltarStateMachine;
import dev.riftal.creator.features.vault.block.SealedChestBlock;
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

    /**
     * Radius, in blocks, of the altar-less {@code /vault unseal} sweep. Used only when there is no
     * altar left to ask - see {@link #unseal}.
     */
    public static final int ORPHAN_CHEST_RADIUS = 16;

    /**
     * Ticks {@code /vault tp} waits after the teleport before it looks for the altar to land on.
     * Long enough for the chunks the teleport ticketed to finish loading, short enough that the
     * operator is still falling into the hall when it happens.
     */
    public static final int TP_SETTLE_TICKS = 20;

    /** Half-width, in blocks, of {@code /vault tp}'s post-teleport standing-spot probe. */
    public static final int TP_PROBE_RADIUS = 4;

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
            // The altar-less recovery path. A Sealed Chest is unbreakable, unopenable, unpushable
            // and not a container, so an altar mined by accident in creative - very easy while
            // dressing a set - otherwise strands its chests permanently: nothing else this feature
            // ships can ever open them again.
            int orphaned = SealedChestBlock.unsealAround(level, BlockPos.containing(source.getPosition()),
                    ORPHAN_CHEST_RADIUS, 0L);
            if (orphaned > 0) {
                return CommandHelper.success(source,
                        Component.translatable("commands.creator_vault.unseal_orphans", orphaned));
            }
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
        if (altar == null || !altar.adoptKeeper(level, keeper)) {
            // A refused adoption (the altar is SPENT, or there is none) used to leave behind a
            // persistent, never-despawning Keeper that no altar owned, so /vault reset could not
            // clean it up. Left ordinary instead: no persistence, no binding, so it despawns like
            // any other mob and the set is not littered between takes.
            LOG.info("[vault] /vault spawn_keeper: no altar took the Keeper at {}, left unbound", pos);
            return CommandHelper.success(source,
                    Component.translatable("commands.creator_vault.spawned_unbound"));
        }
        keeper.setPersistenceRequired();
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
        // Teleport FIRST, refine afterwards.
        //
        // findNearestMapStructure only reads chunks at ChunkStatus.STRUCTURE_STARTS, so the terrain
        // it points at is almost never generated yet. Probing it with Level#getBlockState here
        // would be a blocking generate-and-join on the server thread (getBlockState -> getChunk(x,
        // z, ChunkStatus.FULL, /* requireChunk = */ true), Level.java:372-379) - a multi-second
        // freeze on the very first beat of the clip. ServerPlayer#teleportTo tickets the
        // destination chunks asynchronously, so the landing spot is refined a second later instead.
        BlockPos target = new BlockPos(found.getX() + 8, VaultStructures.VAULT_START_Y + 2,
                found.getZ() + 8);
        player.teleportTo(level, target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        TickScheduler.runLater(TP_SETTLE_TICKS, () -> refineVaultLanding(level, player, target))
                .tag(VaultFeature.SCHED_ALTAR);
        return CommandHelper.success(source, Component.translatable("commands.creator_vault.tp",
                target.getX(), target.getY(), target.getZ()));
    }

    /**
     * Second half of {@code /vault tp}, run {@link #TP_SETTLE_TICKS} ticks after the teleport, once
     * the destination chunks have actually loaded: put the operator on the Cursed Altar if the
     * vault really did generate one, otherwise on the nearest bit of floor they can stand on.
     *
     * <p>Every lookup below is loaded-chunks-only. {@link #nearestAltar} walks chunk block-entity
     * maps, and {@link #findStandingSpot} refuses to touch an unloaded column, so nothing here can
     * generate terrain however far off the estimate was.
     */
    private static void refineVaultLanding(ServerLevel level, ServerPlayer player, BlockPos landed) {
        if (player.isRemoved() || player.level() != level) {
            return;
        }
        CursedAltarBlockEntity altar = nearestAltar(level, Vec3.atCenterOf(landed), MAX_ALTAR_RADIUS);
        BlockPos target = altar != null ? altar.getBlockPos().above() : findStandingSpot(level, landed);
        if (target == null || target.equals(landed)) {
            return;
        }
        player.teleportTo(level, target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
    }

    /**
     * Nearest place with a floor and two blocks of headroom, in a narrow column band around
     * {@code centre}. Loaded chunks only; returns {@code null} when nothing qualifies.
     */
    @Nullable
    private static BlockPos findStandingSpot(ServerLevel level, BlockPos centre) {
        for (int y = VaultStructures.VAULT_START_Y + 8; y >= VaultStructures.VAULT_START_Y - 4; y--) {
            for (int dx = -TP_PROBE_RADIUS; dx <= TP_PROBE_RADIUS; dx++) {
                for (int dz = -TP_PROBE_RADIUS; dz <= TP_PROBE_RADIUS; dz++) {
                    BlockPos probe = new BlockPos(centre.getX() + dx, y, centre.getZ() + dz);
                    if (!level.isLoaded(probe)) {
                        continue;
                    }
                    if (level.getBlockState(probe).isAir()
                            && level.getBlockState(probe.above()).isAir()
                            && !level.getBlockState(probe.below()).isAir()) {
                        return probe;
                    }
                }
            }
        }
        return null;
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
