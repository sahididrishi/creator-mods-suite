package dev.riftal.creator.features.vault.block.entity;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.core.util.Selection;
import dev.riftal.creator.features.vault.VaultFeature;
import dev.riftal.creator.features.vault.block.AltarState;
import dev.riftal.creator.features.vault.block.AltarStateMachine;
import dev.riftal.creator.features.vault.block.CursedAltarBlock;
import dev.riftal.creator.features.vault.block.SealedChestBlock;
import dev.riftal.creator.features.vault.entity.VaultKeeper;
import dev.riftal.creator.features.vault.net.VaultStatusPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * The brain of one Cursed Altar.
 *
 * <p>Owns the charge countdown, the UUID of the Vault Keeper it summoned, and the positions of the
 * Sealed Chests it will crack open when that Keeper dies. The visible state lives in the
 * blockstate ({@link CursedAltarBlock#STATE}) so vanilla block syncing carries it to clients for
 * free; the numbers behind it reach the HUD through {@link VaultStatusPayload}.
 *
 * <p>Server side only: {@code CursedAltarBlock#getTicker} hands out a ticker on the logical server
 * exclusively, and every method here bails out unless it is looking at a {@link ServerLevel}.
 */
public class CursedAltarBlockEntity extends BlockEntity {

    /**
     * One Sealed Chest this altar is responsible for: where it is, which way it faced, and the loot
     * it was carrying.
     *
     * <p>The loot table and seed are remembered so {@code /vault reset} can put back the chest that
     * was there rather than a default one. A set dressed with a custom-loot Sealed Chest in the
     * structure NBT would otherwise silently revert to {@code creator_vault:chests/cursed_vault}
     * after the first reset - i.e. between takes.
     */
    public record StoredChest(BlockPos pos, Direction facing, ResourceKey<LootTable> lootTable,
                              long lootTableSeed) {

        /** A chest carrying this feature's default table and a fresh roll on first open. */
        public StoredChest(BlockPos pos, Direction facing) {
            this(pos, facing, VaultFeature.CHEST_LOOT_TABLE, 0L);
        }
    }

    private int chargeTicks;
    private int missingKeeperTicks;
    private int ageTicks;
    private long lootSeed;
    @Nullable
    private UUID keeperId;
    private float lastKeeperHealth = -1.0F;
    /** False until {@link #tickActive} has polled once since this block entity was created/loaded. */
    private boolean polledSinceLoad;
    private final List<StoredChest> sealedChests = new ArrayList<>();

    /**
     * Keepers this altar told to go away but could not reach, because their chunk was not loaded at
     * the time. Persisted, and checked by {@link #onKeeperDead(ServerLevel, UUID)}: a Keeper on this
     * list can never unseal the altar again, however it eventually dies.
     */
    private final Set<UUID> staleKeepers = new LinkedHashSet<>();

    /** Ceiling on {@link #staleKeepers}, so it cannot grow the saved NBT without limit. */
    private static final int MAX_STALE_KEEPERS = 16;

    /** Ceiling on {@link #clientStateTicks} - it only has to outlast the charge, not overflow. */
    private static final int CLIENT_TICK_CAP = AltarStateMachine.CHARGE_TICKS * 4;

    /** Client-only animation clock; never saved, never read on the server. */
    private int clientStateTicks;
    @Nullable
    private AltarState clientState;

    public CursedAltarBlockEntity(BlockPos pos, BlockState blockState) {
        super(VaultFeature.CURSED_ALTAR_BE.get(), pos, blockState);
    }

    // ------------------------------------------------------------------ state

    /** The altar's state, read from the blockstate, which is the single source of truth. */
    public AltarState state() {
        BlockState blockState = getBlockState();
        if (blockState.getBlock() instanceof CursedAltarBlock) {
            return blockState.getValue(CursedAltarBlock.STATE);
        }
        return AltarState.SEALED;
    }

    public int chargeTicks() {
        return this.chargeTicks;
    }

    public int sealedChestCount() {
        return this.sealedChests.size();
    }

    public List<StoredChest> sealedChests() {
        return List.copyOf(this.sealedChests);
    }

    @Nullable
    public UUID keeperId() {
        return this.keeperId;
    }

    public long lootSeed() {
        return this.lootSeed;
    }

    /** Ticks the bound Keeper has been unresolvable. {@code /vault status} shows the countdown. */
    public int missingKeeperTicks() {
        return this.missingKeeperTicks;
    }

    private void applyEvent(AltarStateMachine.Event event) {
        AltarState from = state();
        AltarState to = AltarStateMachine.next(from, event);
        if (to == from) {
            return;
        }
        BlockState blockState = getBlockState();
        if (!(blockState.getBlock() instanceof CursedAltarBlock)) {
            return;
        }
        BlockState updated = blockState.setValue(CursedAltarBlock.STATE, to);
        if (this.level != null) {
            this.level.setBlock(this.worldPosition, updated, Block.UPDATE_ALL);
        }
        setChanged();
    }

    // ------------------------------------------------------------- activation

    /**
     * Accepts a Vault Key. Scans for Sealed Chests, starts the charge countdown and rolls the loot
     * seed the chests will use.
     *
     * @param player the player who offered the key, or {@code null} for a command/test activation
     * @return true when the altar actually moved to {@link AltarState#CHARGING}
     */
    public boolean activate(@Nullable Player player) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!AltarStateMachine.acceptsKey(state())) {
            return false;
        }

        rescanChests(serverLevel);
        this.chargeTicks = 0;
        this.missingKeeperTicks = 0;
        this.ageTicks = 0;
        this.keeperId = null;
        this.lastKeeperHealth = -1.0F;
        this.lootSeed = serverLevel.getRandom().nextLong();
        applyEvent(AltarStateMachine.Event.KEY);
        setChanged();

        Vec3 centre = Vec3.atCenterOf(this.worldPosition).add(0.0D, 0.6D, 0.0D);
        Fx.sound(serverLevel, centre, VaultFeature.ALTAR_ACTIVATE.get(), SoundSource.BLOCKS, 1.0F, 0.8F);
        Fx.particleRing(serverLevel, ParticleTypes.SOUL_FIRE_FLAME, centre, 1.4D, 16);

        if (player != null) {
            VaultFeature.KEYS_USED.update(player, used -> used + 1);
        }
        // Push straight away rather than waiting for the first STATUS_BROADCAST_INTERVAL tick, so
        // the HUD bar is on screen on the same frame the key disappears from the hand.
        broadcastStatus(serverLevel);
        LOG.info("[vault] altar at {} primed, {} sealed chest(s) wired", this.worldPosition,
                this.sealedChests.size());
        return true;
    }

    /**
     * Re-reads the Sealed Chests around the altar. Called once per activation, never per tick.
     *
     * <p>Walks the loaded chunks' block-entity maps instead of calling {@code getBlockState} on
     * every position in a 33-cube. That is a few hundred map lookups instead of 35,937 reads, and
     * more importantly {@code Level#getBlockState} generates the chunk it is handed
     * ({@code getChunk(x, z, ChunkStatus.FULL, true)}), so the block scan could block the server
     * thread on terrain generation on the exact tick the key goes in. Every Sealed Chest has a
     * block entity, so nothing is missed.
     */
    private void rescanChests(ServerLevel serverLevel) {
        this.sealedChests.clear();
        int r = AltarStateMachine.CHEST_SCAN_RADIUS;
        double radiusSqr = (double) r * r;
        int minChunkX = SectionPos.blockToSectionCoord(this.worldPosition.getX() - r);
        int maxChunkX = SectionPos.blockToSectionCoord(this.worldPosition.getX() + r);
        int minChunkZ = SectionPos.blockToSectionCoord(this.worldPosition.getZ() - r);
        int maxChunkZ = SectionPos.blockToSectionCoord(this.worldPosition.getZ() + r);

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!serverLevel.getChunkSource().hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = serverLevel.getChunk(cx, cz);
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos candidate = entry.getKey();
                    if (candidate.distSqr(this.worldPosition) > radiusSqr) {
                        continue;
                    }
                    if (!(entry.getValue() instanceof SealedChestBlockEntity sealed)) {
                        continue;
                    }
                    BlockState found = chunk.getBlockState(candidate);
                    if (!(found.getBlock() instanceof SealedChestBlock)) {
                        continue;
                    }
                    this.sealedChests.add(new StoredChest(candidate.immutable(),
                            found.getValue(SealedChestBlock.FACING),
                            sealed.lootTable(), sealed.lootTableSeed()));
                }
            }
        }
    }

    // ------------------------------------------------------------------ ticks

    /**
     * Client ticker: counts how long this altar has been showing its current state.
     *
     * <p>{@code chargeTicks} is server-only - it is never synced - so the crystal renderer has
     * nothing to interpolate the 60-tick rise against. It does not need it: the client knows
     * exactly when the state flipped, because that is the tick the block update arrived, so
     * counting from there is the same number. A counter bump, not a scan.
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                  CursedAltarBlockEntity altar) {
        if (!(state.getBlock() instanceof CursedAltarBlock)) {
            return;
        }
        AltarState now = state.getValue(CursedAltarBlock.STATE);
        if (now != altar.clientState) {
            altar.clientState = now;
            altar.clientStateTicks = 0;
        } else if (altar.clientStateTicks < CLIENT_TICK_CAP) {
            altar.clientStateTicks++;
        }
    }

    /** How many ticks the client has been showing the current state. Renderer only. */
    public int clientStateTicks() {
        return this.clientStateTicks;
    }

    /** Server ticker. Wired up by {@code CursedAltarBlock#getTicker}; never runs on a client. */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  CursedAltarBlockEntity altar) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(state.getBlock() instanceof CursedAltarBlock)) {
            return;
        }
        AltarState altarState = state.getValue(CursedAltarBlock.STATE);
        if (!altarState.isTicking()) {
            return;
        }

        altar.ageTicks++;
        if (altarState == AltarState.CHARGING) {
            altar.tickCharging(serverLevel, pos);
        } else if (altarState == AltarState.ACTIVE) {
            altar.tickActive(serverLevel);
        }
        if (altar.ageTicks % AltarStateMachine.STATUS_BROADCAST_INTERVAL == 0) {
            altar.broadcastStatus(serverLevel);
        }
    }

    private void tickCharging(ServerLevel serverLevel, BlockPos pos) {
        this.chargeTicks++;
        if (this.chargeTicks % 5 == 0) {
            Vec3 centre = Vec3.atCenterOf(pos).add(0.0D, 0.8D, 0.0D);
            double radius = 1.6D - 1.2D * AltarStateMachine.chargeProgress(AltarState.CHARGING,
                    this.chargeTicks);
            Fx.particleRing(serverLevel, ParticleTypes.PORTAL, centre, Math.max(0.2D, radius), 12);
        }
        if (this.chargeTicks >= AltarStateMachine.CHARGE_TICKS) {
            summonKeeper(serverLevel, pos);
            return;
        }
        // Cheap chunk-dirty flag, not a write: without it the charge counter is only durable at the
        // state edges, and the plan's relog-mid-charge case resumes from a stale ChargeTicks.
        setChanged();
    }

    private void tickActive(ServerLevel serverLevel) {
        boolean scheduled = this.ageTicks % AltarStateMachine.KEEPER_CHECK_INTERVAL == 0;
        // One extra poll on the first tick after a load: lastKeeperHealth is derived, not persisted,
        // so without it the HUD's Keeper bar is missing for up to a full interval after a relog.
        boolean catchUp = !this.polledSinceLoad;
        if (!scheduled && !catchUp) {
            return;
        }
        this.polledSinceLoad = true;

        VaultKeeper keeper = resolveKeeper(serverLevel);
        if (keeper != null && keeper.isAlive()) {
            this.missingKeeperTicks = 0;
            float max = keeper.getMaxHealth();
            this.lastKeeperHealth = max > 0.0F ? keeper.getHealth() / max : 0.0F;
            return;
        }
        this.lastKeeperHealth = -1.0F;
        if (!scheduled) {
            // The catch-up poll must not inflate the missing-Keeper countdown.
            return;
        }
        this.missingKeeperTicks += AltarStateMachine.KEEPER_CHECK_INTERVAL;
        // Cheap chunk-dirty flag: the countdown has to survive an unclean shutdown or a reload
        // restarts the 100-tick grace period the Keeper-in-an-unloaded-chunk case depends on.
        setChanged();
        if (this.missingKeeperTicks >= AltarStateMachine.KEEPER_MISSING_LIMIT) {
            onKeeperDead(serverLevel);
        }
    }

    /** The bound Keeper, if it is loaded and still a Keeper. */
    @Nullable
    public VaultKeeper resolveKeeper(ServerLevel serverLevel) {
        if (this.keeperId == null) {
            return null;
        }
        Entity entity = serverLevel.getEntity(this.keeperId);
        return entity instanceof VaultKeeper keeper ? keeper : null;
    }

    // ----------------------------------------------------------------- keeper

    /**
     * Spawns the Vault Keeper next to the altar and binds it. Driven by the charge countdown;
     * {@code /vault spawn_keeper} spawns its own Keeper wherever the operator is standing and hands
     * it over with {@link #adoptKeeper}.
     *
     * @return the spawned Keeper, or {@code null} when the entity could not be created
     */
    @Nullable
    public VaultKeeper summonKeeper(ServerLevel serverLevel, BlockPos pos) {
        BlockPos spawnPos = findSpawnPos(serverLevel, pos);
        VaultKeeper keeper = VaultFeature.VAULT_KEEPER.get()
                .spawn(serverLevel, spawnPos, MobSpawnType.TRIGGERED);
        if (keeper == null) {
            // Fail open rather than soft-locking the altar in CHARGING forever: treat it as a
            // Keeper that died instantly, so the chests still open and the take is salvageable.
            LOG.warn("[vault] altar at {} could not spawn a Vault Keeper", pos);
            onKeeperDead(serverLevel);
            return null;
        }
        retireBoundKeeper(serverLevel, keeper);
        keeper.bindToAltar(pos);
        keeper.setPersistenceRequired();
        this.keeperId = keeper.getUUID();
        this.staleKeepers.remove(keeper.getUUID());
        this.missingKeeperTicks = 0;
        this.lastKeeperHealth = 1.0F;
        this.polledSinceLoad = true;
        applyEvent(AltarStateMachine.Event.CHARGE_COMPLETE);
        setChanged();

        Vec3 centre = Vec3.atCenterOf(spawnPos);
        Fx.sound(serverLevel, centre, VaultFeature.KEEPER_SUMMON.get(), SoundSource.HOSTILE, 1.2F, 0.9F);
        Fx.particles(serverLevel, ParticleTypes.SCULK_SOUL, centre, 40, 0.7D, 0.05D);
        TickScheduler.runRepeating(4, 5,
                () -> Fx.particles(serverLevel, ParticleTypes.LARGE_SMOKE, centre, 12, 0.6D, 0.02D))
                .tag(VaultFeature.SCHED_ALTAR);
        LOG.info("[vault] Vault Keeper summoned at {} for altar {}", spawnPos, pos);
        return keeper;
    }

    /**
     * Binds an already-spawned Keeper to this altar, fast-forwarding the altar into
     * {@link AltarState#ACTIVE} so that Keeper's death unseals the chests. This is what
     * {@code /vault spawn_keeper} uses: the operator gets a Keeper where they are standing, and it
     * is still wired to the nearest altar.
     *
     * @return false when the altar is spent and cannot take another Keeper
     */
    public boolean adoptKeeper(ServerLevel serverLevel, VaultKeeper keeper) {
        if (state() == AltarState.SEALED) {
            rescanChests(serverLevel);
            this.lootSeed = serverLevel.getRandom().nextLong();
            applyEvent(AltarStateMachine.Event.KEY);
        }
        if (state() == AltarState.CHARGING) {
            applyEvent(AltarStateMachine.Event.CHARGE_COMPLETE);
        }
        if (state() != AltarState.ACTIVE) {
            return false;
        }
        // An altar can only be waiting on one Keeper. Without this the previous one keeps its
        // altarPos, its persistence and its boss bar forever - /vault reset only ever discards the
        // bound one - and its eventual death would unseal an altar mid-fight.
        retireBoundKeeper(serverLevel, keeper);
        this.chargeTicks = AltarStateMachine.CHARGE_TICKS;
        this.missingKeeperTicks = 0;
        this.ageTicks = 0;
        this.lastKeeperHealth = 1.0F;
        this.polledSinceLoad = true;
        this.keeperId = keeper.getUUID();
        this.staleKeepers.remove(keeper.getUUID());
        keeper.bindToAltar(this.worldPosition);
        setChanged();
        broadcastStatus(serverLevel);
        LOG.info("[vault] altar at {} adopted Vault Keeper {}", this.worldPosition, keeper.getUUID());
        return true;
    }

    /**
     * Gets rid of whichever Keeper this altar is currently bound to, so it can bind to
     * {@code replacement} instead.
     *
     * <p>If the old Keeper is loaded it is discarded outright. If it is not - its chunk is out - its
     * UUID goes on {@link #staleKeepers}, which is persisted, so that when it does eventually die
     * its death report is ignored instead of unsealing a re-armed altar.
     */
    private void retireBoundKeeper(ServerLevel serverLevel, @Nullable VaultKeeper replacement) {
        if (this.keeperId == null) {
            return;
        }
        if (replacement != null && this.keeperId.equals(replacement.getUUID())) {
            return;
        }
        VaultKeeper bound = resolveKeeper(serverLevel);
        if (bound != null) {
            bound.bindToAltar(null);
            bound.discard();
        } else {
            // Bounded, so a pathological take-after-take loop cannot grow the block entity's NBT
            // without limit. Oldest first: a Keeper this far back is long dead already.
            while (this.staleKeepers.size() >= MAX_STALE_KEEPERS) {
                Iterator<UUID> oldest = this.staleKeepers.iterator();
                oldest.next();
                oldest.remove();
            }
            this.staleKeepers.add(this.keeperId);
            LOG.warn("[vault] altar at {} could not reach Keeper {} to retire it (chunk not loaded);"
                    + " its death will be ignored", this.worldPosition, this.keeperId);
        }
        this.keeperId = null;
    }

    /**
     * Discards every loaded Keeper that still thinks it belongs to this altar, whether or not it is
     * the one {@link #keeperId} names. That covers Keepers orphaned by an earlier take and the
     * {@code /vault spawn_keeper} Keeper an altar refused to adopt.
     *
     * @return how many were discarded
     */
    private int sweepBoundKeepers(ServerLevel serverLevel) {
        int swept = 0;
        double radius = AltarStateMachine.CHEST_SCAN_RADIUS * 3.0D;
        for (VaultKeeper keeper : Selection.around(serverLevel, VaultKeeper.class,
                Vec3.atCenterOf(this.worldPosition), radius,
                candidate -> this.worldPosition.equals(candidate.altarPos()))) {
            keeper.bindToAltar(null);
            keeper.discard();
            this.staleKeepers.remove(keeper.getUUID());
            swept++;
        }
        return swept;
    }

    /**
     * First breathing space next to the altar, falling back to the block directly above it.
     *
     * <p>Checks three blocks of headroom, not two: the Keeper is 2.3 blocks tall, so a two-block
     * pocket spawns it inside the ceiling.
     */
    private static BlockPos findSpawnPos(ServerLevel serverLevel, BlockPos altarPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int distance = 1; distance <= 2; distance++) {
                BlockPos candidate = altarPos.relative(direction, distance);
                if (!serverLevel.isLoaded(candidate)) {
                    continue;
                }
                if (serverLevel.getBlockState(candidate).isAir()
                        && serverLevel.getBlockState(candidate.above()).isAir()
                        && serverLevel.getBlockState(candidate.above(2)).isAir()) {
                    return candidate;
                }
            }
        }
        return altarPos.above();
    }

    /**
     * The Keeper is gone: unseal every chest this altar knows about and burn out.
     *
     * <p>Called from the poll in {@link #tickActive}, from {@code VaultKeeper#die} for an instant
     * reaction, and from {@code /vault unseal}. Safe to call more than once.
     *
     * @return the number of chests that were actually unsealed by this call
     */
    public int onKeeperDead(ServerLevel serverLevel) {
        return onKeeperDead(serverLevel, null);
    }

    /**
     * Same, but only when {@code reporter} is the Keeper this altar is actually waiting on.
     *
     * <p>{@code VaultKeeper#die} calls this with its own UUID. Anything else - a Keeper this altar
     * was made to give up on, one left over from an earlier take, one caught by a stray
     * {@code /kill @e[type=creator_vault:vault_keeper]} - is ignored, so it cannot crack the chest
     * open while the real Keeper is at full health.
     *
     * @param reporter the dying Keeper's UUID, or {@code null} for the unconditional
     *                 command/poll path
     * @return the number of chests that were actually unsealed by this call
     */
    public int onKeeperDead(ServerLevel serverLevel, @Nullable UUID reporter) {
        if (reporter != null) {
            if (this.staleKeepers.contains(reporter)) {
                return 0;
            }
            // Not "equals if set": an altar with nothing bound must reject every entity report.
            // Otherwise a stray Keeper dying during the 60-tick charge - when keeperId is still
            // null - would crack the chest open before the real one had even been summoned.
            if (!reporter.equals(this.keeperId)) {
                return 0;
            }
        }
        AltarState current = state();
        if (current == AltarState.SPENT || current == AltarState.SEALED) {
            return 0;
        }
        int unsealed = 0;
        for (StoredChest chest : this.sealedChests) {
            if (!serverLevel.isLoaded(chest.pos())) {
                continue;
            }
            if (serverLevel.getBlockState(chest.pos()).getBlock() instanceof SealedChestBlock) {
                SealedChestBlock.unseal(serverLevel, chest.pos(), this.lootSeed);
                unsealed++;
            }
        }
        this.missingKeeperTicks = 0;
        this.lastKeeperHealth = -1.0F;
        if (current == AltarState.CHARGING) {
            // Cut short by /vault unseal or by a failed summon: finish the charge on the spot so
            // the state machine can move CHARGING -> ACTIVE -> SPENT in one go.
            applyEvent(AltarStateMachine.Event.CHARGE_COMPLETE);
        }
        applyEvent(AltarStateMachine.Event.KEEPER_DEAD);
        setChanged();

        Vec3 centre = Vec3.atCenterOf(this.worldPosition).add(0.0D, 0.7D, 0.0D);
        Fx.sound(serverLevel, centre, VaultFeature.ALTAR_UNSEAL.get(), SoundSource.BLOCKS, 1.0F, 0.7F);
        Fx.particles(serverLevel, ParticleTypes.SMOKE, centre, 25, 0.4D, 0.01D);
        broadcastStatus(serverLevel);
        LOG.info("[vault] altar at {} spent, {} chest(s) unsealed", this.worldPosition, unsealed);
        return unsealed;
    }

    /**
     * Re-arms the altar: the Keeper is discarded, every chest this altar opened is re-sealed with
     * its original facing and its contents thrown away, and the state goes back to
     * {@link AltarState#SEALED}. This is the "re-record the take" button.
     *
     * @return the number of chests that were put back
     */
    public int reset(ServerLevel serverLevel) {
        // retireBoundKeeper discards the bound Keeper if it is loaded and blacklists its UUID if it
        // is not; the sweep then catches anything else in the room that still points at this altar.
        retireBoundKeeper(serverLevel, null);
        int swept = sweepBoundKeepers(serverLevel);
        if (swept > 0) {
            LOG.info("[vault] altar at {} swept {} stray Keeper(s) on reset", this.worldPosition, swept);
        }
        this.keeperId = null;
        this.chargeTicks = 0;
        this.missingKeeperTicks = 0;
        this.ageTicks = 0;
        this.lastKeeperHealth = -1.0F;
        this.polledSinceLoad = true;

        int resealed = 0;
        for (StoredChest chest : this.sealedChests) {
            if (!serverLevel.isLoaded(chest.pos())) {
                continue;
            }
            BlockState here = serverLevel.getBlockState(chest.pos());
            if (here.getBlock() instanceof SealedChestBlock) {
                continue;
            }
            if (here.isAir() || here.getBlock() instanceof ChestBlock) {
                Clearable.tryClear(serverLevel.getBlockEntity(chest.pos()));
                // The chest's own table and seed, not the default: a set dressed with a
                // custom-loot Sealed Chest must come back carrying the same loot.
                SealedChestBlock.reseal(serverLevel, chest.pos(), chest.facing(),
                        chest.lootTable(), chest.lootTableSeed());
                resealed++;
            }
        }
        applyEvent(AltarStateMachine.Event.RESET);
        setChanged();

        Vec3 centre = Vec3.atCenterOf(this.worldPosition).add(0.0D, 0.7D, 0.0D);
        Fx.sound(serverLevel, centre, VaultFeature.ALTAR_RESET.get(), SoundSource.BLOCKS, 0.9F, 1.1F);
        Fx.particles(serverLevel, ParticleTypes.ENCHANT, centre, 20, 0.5D, 0.02D);
        broadcastStatus(serverLevel);
        LOG.info("[vault] altar at {} reset, {} chest(s) re-sealed", this.worldPosition, resealed);
        return resealed;
    }

    /**
     * Forgets the Keeper - used when the altar block itself is removed.
     *
     * <p>The Keeper is left alive, as the plan's failure-mode table asks, but its own binding is
     * cleared too when it is loaded: an altar that no longer exists must not keep tethering it, and
     * its eventual death must not report to whatever ends up at that position next.
     */
    public void unbindKeeper() {
        if (this.level instanceof ServerLevel serverLevel) {
            VaultKeeper keeper = resolveKeeper(serverLevel);
            if (keeper != null) {
                keeper.bindToAltar(null);
            }
        }
        this.keeperId = null;
        this.lastKeeperHealth = -1.0F;
    }

    // -------------------------------------------------------------------- net

    /**
     * Pushes the HUD payload to every player close enough to care, <em>including</em> spectators.
     *
     * <p>Not {@code Selection.playersAround}, which filters spectators out: spectator is the mode
     * the plan's fly-through shots are recorded in, and it is how the altar gets framed without a
     * hand in shot. The payload is read-only status, so there is nothing to withhold.
     */
    public void broadcastStatus(ServerLevel serverLevel) {
        Vec3 centre = Vec3.atCenterOf(this.worldPosition);
        for (ServerPlayer player : Selection.around(serverLevel, ServerPlayer.class, centre,
                AltarStateMachine.STATUS_BROADCAST_RANGE, player -> true)) {
            Payloads.sendToPlayer(player, VaultStatusPayload.of(this.worldPosition, state(),
                    this.chargeTicks, this.sealedChests.size(), this.lastKeeperHealth,
                    VaultFeature.KEYS_USED.get(player)));
        }
    }

    // ------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("ChargeTicks", this.chargeTicks);
        tag.putInt("MissingKeeperTicks", this.missingKeeperTicks);
        tag.putLong("LootSeed", this.lootSeed);
        tag.putFloat("KeeperHealth", this.lastKeeperHealth);
        if (this.keeperId != null) {
            tag.putUUID("Keeper", this.keeperId);
        }
        ListTag chests = new ListTag();
        for (StoredChest chest : this.sealedChests) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", chest.pos().getX());
            entry.putInt("Y", chest.pos().getY());
            entry.putInt("Z", chest.pos().getZ());
            entry.putString("Facing", chest.facing().getName());
            entry.putString("LootTable", chest.lootTable().location().toString());
            entry.putLong("LootSeed", chest.lootTableSeed());
            chests.add(entry);
        }
        tag.put("SealedChests", chests);

        ListTag stale = new ListTag();
        for (UUID id : this.staleKeepers) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            stale.add(entry);
        }
        tag.put("StaleKeepers", stale);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.chargeTicks = tag.getInt("ChargeTicks");
        this.missingKeeperTicks = tag.getInt("MissingKeeperTicks");
        this.lootSeed = tag.getLong("LootSeed");
        this.lastKeeperHealth = tag.contains("KeeperHealth") ? tag.getFloat("KeeperHealth") : -1.0F;
        this.polledSinceLoad = false;
        this.keeperId = tag.hasUUID("Keeper") ? tag.getUUID("Keeper") : null;
        this.sealedChests.clear();
        ListTag chests = tag.getList("SealedChests", Tag.TAG_COMPOUND);
        for (int i = 0; i < chests.size(); i++) {
            CompoundTag entry = chests.getCompound(i);
            Direction facing = Direction.byName(entry.getString("Facing"));
            this.sealedChests.add(new StoredChest(
                    new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")),
                    facing == null ? Direction.NORTH : facing,
                    readLootTable(entry),
                    entry.getLong("LootSeed")));
        }
        this.staleKeepers.clear();
        ListTag stale = tag.getList("StaleKeepers", Tag.TAG_COMPOUND);
        for (int i = 0; i < stale.size(); i++) {
            CompoundTag entry = stale.getCompound(i);
            if (entry.hasUUID("Id")) {
                this.staleKeepers.add(entry.getUUID("Id"));
            }
        }
    }

    /** The stored chest's loot table id, falling back to this feature's default. */
    private static ResourceKey<LootTable> readLootTable(CompoundTag entry) {
        if (!entry.contains("LootTable")) {
            return VaultFeature.CHEST_LOOT_TABLE;
        }
        ResourceLocation id = ResourceLocation.tryParse(entry.getString("LootTable"));
        return id == null ? VaultFeature.CHEST_LOOT_TABLE : ResourceKey.create(Registries.LOOT_TABLE, id);
    }
}
