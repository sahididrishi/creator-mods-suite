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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
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

    /** One Sealed Chest this altar is responsible for, and the way it was facing when sealed. */
    public record StoredChest(BlockPos pos, Direction facing) {
    }

    private int chargeTicks;
    private int missingKeeperTicks;
    private int ageTicks;
    private long lootSeed;
    @Nullable
    private UUID keeperId;
    private float lastKeeperHealth = -1.0F;
    private final List<StoredChest> sealedChests = new ArrayList<>();

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

    /** Re-reads the Sealed Chests around the altar. Called once per activation, never per tick. */
    private void rescanChests(ServerLevel serverLevel) {
        this.sealedChests.clear();
        int r = AltarStateMachine.CHEST_SCAN_RADIUS;
        BlockPos min = this.worldPosition.offset(-r, -r, -r);
        BlockPos max = this.worldPosition.offset(r, r, r);
        for (BlockPos scan : BlockPos.betweenClosed(min, max)) {
            BlockState found = serverLevel.getBlockState(scan);
            if (found.getBlock() instanceof SealedChestBlock) {
                this.sealedChests.add(
                        new StoredChest(scan.immutable(), found.getValue(SealedChestBlock.FACING)));
            }
        }
    }

    // ------------------------------------------------------------------ ticks

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
        }
    }

    private void tickActive(ServerLevel serverLevel) {
        if (this.ageTicks % AltarStateMachine.KEEPER_CHECK_INTERVAL != 0) {
            return;
        }
        VaultKeeper keeper = resolveKeeper(serverLevel);
        if (keeper != null && keeper.isAlive()) {
            this.missingKeeperTicks = 0;
            float max = keeper.getMaxHealth();
            this.lastKeeperHealth = max > 0.0F ? keeper.getHealth() / max : 0.0F;
            return;
        }
        this.missingKeeperTicks += AltarStateMachine.KEEPER_CHECK_INTERVAL;
        this.lastKeeperHealth = -1.0F;
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
        keeper.bindToAltar(pos);
        keeper.setPersistenceRequired();
        this.keeperId = keeper.getUUID();
        this.missingKeeperTicks = 0;
        this.lastKeeperHealth = 1.0F;
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
        this.chargeTicks = AltarStateMachine.CHARGE_TICKS;
        this.missingKeeperTicks = 0;
        this.ageTicks = 0;
        this.lastKeeperHealth = 1.0F;
        this.keeperId = keeper.getUUID();
        keeper.bindToAltar(this.worldPosition);
        setChanged();
        broadcastStatus(serverLevel);
        LOG.info("[vault] altar at {} adopted Vault Keeper {}", this.worldPosition, keeper.getUUID());
        return true;
    }

    /** First breathing space next to the altar, falling back to the block directly above it. */
    private static BlockPos findSpawnPos(ServerLevel serverLevel, BlockPos altarPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int distance = 1; distance <= 2; distance++) {
                BlockPos candidate = altarPos.relative(direction, distance);
                if (serverLevel.getBlockState(candidate).isAir()
                        && serverLevel.getBlockState(candidate.above()).isAir()) {
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
        AltarState current = state();
        if (current == AltarState.SPENT || current == AltarState.SEALED) {
            return 0;
        }
        int unsealed = 0;
        for (StoredChest chest : this.sealedChests) {
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
        VaultKeeper keeper = resolveKeeper(serverLevel);
        if (keeper != null) {
            keeper.discard();
        }
        this.keeperId = null;
        this.chargeTicks = 0;
        this.missingKeeperTicks = 0;
        this.ageTicks = 0;
        this.lastKeeperHealth = -1.0F;

        int resealed = 0;
        for (StoredChest chest : this.sealedChests) {
            BlockState here = serverLevel.getBlockState(chest.pos());
            if (here.getBlock() instanceof SealedChestBlock) {
                continue;
            }
            if (here.isAir() || here.getBlock() instanceof ChestBlock) {
                Clearable.tryClear(serverLevel.getBlockEntity(chest.pos()));
                SealedChestBlock.reseal(serverLevel, chest.pos(), chest.facing());
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

    /** Forgets the Keeper without touching it - used when the altar block itself is removed. */
    public void unbindKeeper() {
        this.keeperId = null;
        this.lastKeeperHealth = -1.0F;
    }

    // -------------------------------------------------------------------- net

    /** Pushes the HUD payload to every player close enough to care. */
    public void broadcastStatus(ServerLevel serverLevel) {
        Vec3 centre = Vec3.atCenterOf(this.worldPosition);
        for (ServerPlayer player :
                Selection.playersAround(serverLevel, centre, AltarStateMachine.STATUS_BROADCAST_RANGE)) {
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
            chests.add(entry);
        }
        tag.put("SealedChests", chests);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.chargeTicks = tag.getInt("ChargeTicks");
        this.missingKeeperTicks = tag.getInt("MissingKeeperTicks");
        this.lootSeed = tag.getLong("LootSeed");
        this.keeperId = tag.hasUUID("Keeper") ? tag.getUUID("Keeper") : null;
        this.sealedChests.clear();
        ListTag chests = tag.getList("SealedChests", Tag.TAG_COMPOUND);
        for (int i = 0; i < chests.size(); i++) {
            CompoundTag entry = chests.getCompound(i);
            Direction facing = Direction.byName(entry.getString("Facing"));
            this.sealedChests.add(new StoredChest(
                    new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")),
                    facing == null ? Direction.NORTH : facing));
        }
    }
}
