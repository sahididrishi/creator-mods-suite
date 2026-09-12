package dev.riftal.creator.features.vault.gametest;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.vault.VaultFeature;
import dev.riftal.creator.features.vault.block.AltarState;
import dev.riftal.creator.features.vault.block.AltarStateMachine;
import dev.riftal.creator.features.vault.block.CursedAltarBlock;
import dev.riftal.creator.features.vault.block.SealedChestBlock;
import dev.riftal.creator.features.vault.block.entity.CursedAltarBlockEntity;
import dev.riftal.creator.features.vault.block.entity.SealedChestBlockEntity;
import dev.riftal.creator.features.vault.command.VaultCommands;
import dev.riftal.creator.features.vault.entity.VaultKeeper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * GameTest bodies for the {@code vault} feature. Vanilla API only, so both loaders can call them.
 *
 * <p>Add a {@code public static void name(GameTestHelper helper)} here, then one annotated stub in
 * {@code fabric/src/gametest/java/.../VaultFabricGameTests.java} and one in
 * {@code neoforge/src/main/java/.../VaultNeoForgeGameTests.java}.
 *
 * <p>All tests run inside {@code creator_vault:empty}, a 9x9x9 polished-andesite floor, and build
 * their own two-block "treasure room" on it: an altar at {@link #ALTAR} and one Sealed Chest at
 * {@link #CHEST}, three blocks apart and therefore comfortably inside the altar's 16-block scan.
 *
 * <p><b>Why every altar test has its own {@code batch}.</b> {@code StructureGridSpawner} lays test
 * arenas out 5 blocks apart, so the Sealed Chest in the arena next door sits ~14 blocks from this
 * arena's altar - inside {@code AltarStateMachine.CHEST_SCAN_RADIUS}. Two vault tests running side
 * by side in one batch would therefore see, and unseal, each other's chests. A distinct batch name
 * per test serialises them. For the same reason nothing below asserts on a global chest
 * <em>count</em>: the assertions name the exact positions this test owns.
 */
public final class VaultGameTests {

    /** Altar position, relative to the test structure. */
    public static final BlockPos ALTAR = new BlockPos(4, 1, 4);

    /** Sealed Chest position, three blocks from the altar. */
    public static final BlockPos CHEST = new BlockPos(4, 1, 7);

    /** Smoke test: the feature survived the config filter and is live in this session. */
    public static void featureIsEnabled(GameTestHelper helper) {
        helper.assertTrue(CreatorMods.isEnabled(VaultFeature.ID),
                "feature '" + VaultFeature.ID + "' should be enabled in the test session");
        helper.succeed();
    }

    /** A freshly placed altar is dormant and its chest is still sealed. */
    public static void altarStartsSealed(GameTestHelper helper) {
        buildRoom(helper);
        helper.assertBlockProperty(ALTAR, CursedAltarBlock.STATE, AltarState.SEALED);
        helper.assertBlockPresent(VaultFeature.SEALED_CHEST.get(), CHEST);
        helper.assertEntityNotPresent(VaultFeature.VAULT_KEEPER.get());
        helper.succeed();
    }

    /** The whole summon: key -&gt; CHARGING -&gt; (60 ticks) -&gt; ACTIVE with a Keeper on the floor. */
    public static void keyChargesTheAltarAndSummonsTheKeeper(GameTestHelper helper) {
        buildRoom(helper);
        CursedAltarBlockEntity altar = altarAt(helper);
        helper.assertTrue(altar.activate(null), "a sealed altar must accept a key");
        helper.assertBlockProperty(ALTAR, CursedAltarBlock.STATE, AltarState.CHARGING);
        assertChestRecorded(helper, altar, CHEST);

        // Proves the charge really takes time instead of summoning on the same tick.
        helper.runAtTickTime(30L,
                () -> helper.assertEntityNotPresent(VaultFeature.VAULT_KEEPER.get()));
        helper.succeedWhen(() -> {
            helper.assertBlockProperty(ALTAR, CursedAltarBlock.STATE, AltarState.ACTIVE);
            helper.assertEntityPresent(VaultFeature.VAULT_KEEPER.get());
        });
    }

    /** Right-clicking with the key goes through {@code useItemOn} and consumes one key. */
    public static void rightClickWithKeyConsumesIt(GameTestHelper helper) {
        buildRoom(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(VaultFeature.VAULT_KEY.get(), 2));
        helper.useBlock(ALTAR, player);

        helper.assertBlockProperty(ALTAR, CursedAltarBlock.STATE, AltarState.CHARGING);
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        helper.assertTrue(held.getCount() == 1,
                "exactly one key should have been consumed, " + held.getCount() + " left");
        helper.succeed();
    }

    /** A wrong item does nothing at all: no charge, no consumption. */
    public static void wrongItemIsRejected(GameTestHelper helper) {
        buildRoom(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 1));
        helper.useBlock(ALTAR, player);

        helper.assertBlockProperty(ALTAR, CursedAltarBlock.STATE, AltarState.SEALED);
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
                "the stick must not be consumed");
        helper.assertEntityNotPresent(VaultFeature.VAULT_KEEPER.get());
        helper.succeed();
    }

    /** A key offered to an altar that is already charging is refused and not eaten. */
    public static void secondKeyIsRefusedWhileCharging(GameTestHelper helper) {
        buildRoom(helper);
        CursedAltarBlockEntity altar = altarAt(helper);
        helper.assertTrue(altar.activate(null), "a sealed altar must accept a key");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(VaultFeature.VAULT_KEY.get(), 1));
        helper.useBlock(ALTAR, player);

        helper.assertBlockProperty(ALTAR, CursedAltarBlock.STATE, AltarState.CHARGING);
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
                "a refused key must stay in the hand");
        helper.assertTrue(!altar.activate(null), "activate() must refuse a charging altar");
        helper.succeed();
    }

    /** Killing the Keeper cracks the Sealed Chest into a vanilla chest and spends the altar. */
    public static void keeperDeathUnsealsTheChest(GameTestHelper helper) {
        buildRoom(helper);
        CursedAltarBlockEntity altar = altarAt(helper);
        helper.assertTrue(altar.activate(null), "a sealed altar must accept a key");

        VaultKeeper[] keeper = new VaultKeeper[1];
        helper.startSequence()
                .thenWaitUntil(() -> awaitSummonedKeeper(helper, altar, keeper))
                .thenExecute(() -> keeper[0].kill())
                .thenWaitUntil(() -> {
                    helper.assertBlockPresent(Blocks.CHEST, CHEST);
                    helper.assertBlockProperty(ALTAR, CursedAltarBlock.STATE, AltarState.SPENT);
                })
                .thenSucceed();
    }

    /**
     * The summoned Keeper is the one thing between the player and the loot, so it must not despawn
     * and it must know which altar to report to.
     */
    public static void keeperIsPersistentAndBoundToItsAltar(GameTestHelper helper) {
        buildRoom(helper);
        CursedAltarBlockEntity altar = altarAt(helper);
        helper.assertTrue(altar.activate(null), "a sealed altar must accept a key");

        VaultKeeper[] summoned = new VaultKeeper[1];
        helper.startSequence()
                .thenWaitUntil(() -> awaitSummonedKeeper(helper, altar, summoned))
                .thenExecute(() -> {
                    VaultKeeper keeper = summoned[0];
                    helper.assertTrue(keeper.isPersistenceRequired(),
                            "the Keeper must be persistent or it vanishes between takes");
                    helper.assertTrue(!keeper.removeWhenFarAway(4096.0D),
                            "the Keeper must never despawn when the creator walks away");
                    helper.assertTrue(helper.absolutePos(ALTAR).equals(keeper.altarPos()),
                            "expected the Keeper bound to " + helper.absolutePos(ALTAR)
                                    + ", got " + keeper.altarPos());
                    helper.killAllEntities();
                })
                .thenSucceed();
    }

    /**
     * {@code /vault reset}'s core: unseal, then put everything back - same block, same facing,
     * altar armed again. This is the take-after-take loop the whole feature exists for.
     */
    public static void resetReSealsTheChest(GameTestHelper helper) {
        buildRoom(helper);
        CursedAltarBlockEntity altar = altarAt(helper);
        helper.assertTrue(altar.activate(null), "a sealed altar must accept a key");

        int unsealed = altar.onKeeperDead(helper.getLevel());
        helper.assertTrue(unsealed >= 1, "expected at least this altar's chest to unseal");
        helper.assertBlockPresent(Blocks.CHEST, CHEST);
        helper.assertBlockProperty(ALTAR, CursedAltarBlock.STATE, AltarState.SPENT);

        int resealed = altar.reset(helper.getLevel());
        helper.assertTrue(resealed >= 1, "expected at least this altar's chest to re-seal");
        helper.assertBlockPresent(VaultFeature.SEALED_CHEST.get(), CHEST);
        helper.assertBlockProperty(CHEST, SealedChestBlock.FACING, Direction.NORTH);
        helper.assertBlockProperty(ALTAR, CursedAltarBlock.STATE, AltarState.SEALED);
        helper.killAllEntities();
        helper.succeed();
    }

    /** The Sealed Chest cannot be mined - that is the only thing keeping the loot in it. */
    public static void sealedChestIsUnbreakable(GameTestHelper helper) {
        buildRoom(helper);
        BlockPos absolute = helper.absolutePos(CHEST);
        float speed = helper.getLevel().getBlockState(absolute)
                .getDestroySpeed(helper.getLevel(), absolute);
        helper.assertTrue(speed < 0.0F,
                "the Sealed Chest must be unbreakable, destroy speed was " + speed);
        helper.succeed();
    }

    /** The unsealed chest carries this feature's loot table rather than being empty. */
    public static void unsealedChestCarriesTheVaultLootTable(GameTestHelper helper) {
        buildRoom(helper);
        CursedAltarBlockEntity altar = altarAt(helper);
        helper.assertTrue(altar.activate(null), "a sealed altar must accept a key");
        altar.onKeeperDead(helper.getLevel());

        helper.assertBlockPresent(Blocks.CHEST, CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST);
        if (chest == null) {
            helper.fail("unsealing did not leave a ChestBlockEntity", CHEST);
            return;
        }
        helper.assertTrue(VaultFeature.CHEST_LOOT_TABLE.equals(chest.getLootTable()),
                "expected " + VaultFeature.CHEST_LOOT_TABLE.location()
                        + ", got " + chest.getLootTable());
        helper.succeed();
    }

    /**
     * The chest list is taken once, when the key goes in. A Sealed Chest placed afterwards is not
     * part of that take and must stay shut - otherwise a creator dressing the set mid-charge would
     * find props opening themselves.
     */
    public static void altarOnlyOpensChestsItRecordedWhenActivated(GameTestHelper helper) {
        buildRoom(helper);
        CursedAltarBlockEntity altar = altarAt(helper);
        helper.assertTrue(altar.activate(null), "a sealed altar must accept a key");

        BlockPos late = new BlockPos(1, 1, 1);
        helper.setBlock(late, VaultFeature.SEALED_CHEST.get().defaultBlockState()
                .setValue(SealedChestBlock.FACING, Direction.EAST));

        altar.onKeeperDead(helper.getLevel());

        helper.assertBlockPresent(Blocks.CHEST, CHEST);
        helper.assertBlockPresent(VaultFeature.SEALED_CHEST.get(), late);
        helper.succeed();
    }

    /**
     * Relog / chunk-unload cover: everything the altar needs to finish a fight has to survive the
     * block entity being written out and read back.
     */
    public static void altarNbtSurvivesASaveAndLoad(GameTestHelper helper) {
        buildRoom(helper);
        ServerLevel level = helper.getLevel();
        CursedAltarBlockEntity altar = altarAt(helper);
        helper.assertTrue(altar.activate(null), "a sealed altar must accept a key");

        VaultKeeper keeper = altar.summonKeeper(level, helper.absolutePos(ALTAR));
        if (keeper == null) {
            helper.fail("the altar could not summon its Keeper", ALTAR);
            return;
        }

        CompoundTag tag = altar.saveCustomOnly(level.registryAccess());
        CursedAltarBlockEntity copy =
                new CursedAltarBlockEntity(altar.getBlockPos(), altar.getBlockState());
        copy.loadCustomOnly(tag, level.registryAccess());

        helper.assertTrue(copy.lootSeed() == altar.lootSeed(),
                "the loot seed must round-trip, or the chest rolls different loot after a relog");
        helper.assertTrue(keeper.getUUID().equals(copy.keeperId()),
                "expected keeper " + keeper.getUUID() + ", got " + copy.keeperId());
        helper.assertTrue(copy.chargeTicks() == altar.chargeTicks(),
                "charge ticks must round-trip");
        helper.assertTrue(copy.sealedChestCount() == altar.sealedChestCount(),
                "the chest list must round-trip");
        assertChestRecorded(helper, copy, CHEST);

        helper.killAllEntities();
        helper.succeed();
    }

    /**
     * {@code /vault spawn_keeper}'s core: a Keeper spawned anywhere can be handed to an altar, and
     * killing it still opens that altar's chests.
     */
    public static void adoptedKeeperStillUnsealsOnDeath(GameTestHelper helper) {
        buildRoom(helper);
        ServerLevel level = helper.getLevel();
        CursedAltarBlockEntity altar = altarAt(helper);

        VaultKeeper keeper = VaultFeature.VAULT_KEEPER.get()
                .spawn(level, helper.absolutePos(new BlockPos(2, 1, 2)), MobSpawnType.COMMAND);
        if (keeper == null) {
            helper.fail("could not spawn a Vault Keeper");
            return;
        }
        helper.assertTrue(altar.adoptKeeper(level, keeper),
                "a sealed altar must adopt a command-spawned Keeper");
        helper.assertBlockProperty(ALTAR, CursedAltarBlock.STATE, AltarState.ACTIVE);
        helper.assertTrue(helper.absolutePos(ALTAR).equals(keeper.altarPos()),
                "adoption must bind the Keeper back to the altar");
        assertChestRecorded(helper, altar, CHEST);

        keeper.kill();
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.CHEST, CHEST);
            helper.assertBlockProperty(ALTAR, CursedAltarBlock.STATE, AltarState.SPENT);
        });
    }

    /**
     * The "nearest altar" lookup every {@code /vault} sub-command runs on. It walks chunk
     * block-entity maps rather than scanning blocks, so "nearest" has to come out of a real
     * distance comparison and not out of iteration order.
     */
    public static void nearestAltarLookupPicksTheCloserAltar(GameTestHelper helper) {
        BlockPos near = new BlockPos(2, 1, 4);
        BlockPos far = new BlockPos(7, 1, 4);
        helper.setBlock(near, VaultFeature.CURSED_ALTAR.get());
        helper.setBlock(far, VaultFeature.CURSED_ALTAR.get());
        ServerLevel level = helper.getLevel();

        // Radius 6: wide enough for both altars in this arena, far too small to reach the arena
        // next door, which StructureGridSpawner puts 14 blocks away.
        CursedAltarBlockEntity fromNear = VaultCommands.nearestAltar(level,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 1, 4))), 6);
        if (fromNear == null) {
            helper.fail("nearestAltar found no altar at all", near);
            return;
        }
        helper.assertTrue(fromNear.getBlockPos().equals(helper.absolutePos(near)),
                "expected the altar at " + near + ", got " + fromNear.getBlockPos());

        CursedAltarBlockEntity fromFar = VaultCommands.nearestAltar(level,
                Vec3.atCenterOf(helper.absolutePos(far)), 6);
        if (fromFar == null) {
            helper.fail("nearestAltar found no altar at all", far);
            return;
        }
        helper.assertTrue(fromFar.getBlockPos().equals(helper.absolutePos(far)),
                "expected the altar at " + far + ", got " + fromFar.getBlockPos());
        helper.succeed();
    }

    /**
     * End to end through Brigadier: the real {@code /vault reset} node, run from a console source
     * standing on the altar, re-seals the chest and re-arms the altar.
     */
    public static void commandResetReArmsTheNearestAltar(GameTestHelper helper) {
        buildRoom(helper);
        ServerLevel level = helper.getLevel();
        CursedAltarBlockEntity altar = altarAt(helper);
        helper.assertTrue(altar.activate(null), "a sealed altar must accept a key");
        altar.onKeeperDead(level);
        helper.assertBlockPresent(Blocks.CHEST, CHEST);

        CommandSourceStack source = level.getServer().createCommandSourceStack()
                .withLevel(level)
                .withPosition(Vec3.atCenterOf(helper.absolutePos(ALTAR)))
                .withSuppressedOutput();
        // Radius 4 keeps the search inside this arena even when the grid puts another vault test
        // 14 blocks away.
        level.getServer().getCommands().performPrefixedCommand(source, "vault reset 4");

        helper.assertBlockPresent(VaultFeature.SEALED_CHEST.get(), CHEST);
        helper.assertBlockProperty(CHEST, SealedChestBlock.FACING, Direction.NORTH);
        helper.assertBlockProperty(ALTAR, CursedAltarBlock.STATE, AltarState.SEALED);
        helper.killAllEntities();
        helper.succeed();
    }

    /**
     * The anti-kiting tether has to work <em>while the Keeper has a target</em> - that is the only
     * situation it exists for.
     *
     * <p>It used to live in {@code KeeperGuardAltarGoal}, which bails out the moment
     * {@code getTarget() != null}, so a player backing up the corridor - a target for the whole
     * retreat - was never pulled on. It is in {@code VaultKeeper#customServerAiStep} now, which
     * runs every tick whatever the Keeper is doing.
     *
     * <p><b>Why the Keeper is kited straight up.</b> {@link VaultKeeper#TETHER_RADIUS} is wider
     * than any GameTest arena, so a Keeper pushed past it horizontally leaves the chunks
     * {@code StructureUtils} force-loaded for this structure - and an entity outside
     * {@code FullChunkStatus.ENTITY_TICKING} is never ticked at all, so
     * {@code customServerAiStep} never runs and the Keeper sits exactly where the teleport dropped
     * it. That says nothing about the tether.
     *
     * <p>An earlier version of this test called {@code ServerLevel#setChunkForced} on the
     * destination to buy a ticket out there. That is not enough, and it is what made this test go
     * red on CI while it stayed green on every developer machine:
     *
     * <ul>
     *   <li>{@code setChunkForced} loads the chunk to {@code FullChunkStatus.FULL} at once, but
     *       the {@code TicketType.FORCED} ticket it adds at {@code ChunkMap.FORCED_TICKET_LEVEL}
     *       ({@code ChunkLevel.byStatus(ENTITY_TICKING)}) only reaches the chunk when
     *       {@code DistanceManager} next runs its updates - a tick or more later, and later still
     *       on a busy machine.</li>
     *   <li>Until then {@code PersistentEntitySectionManager} has that chunk down as
     *       {@code Visibility.HIDDEN} and queued in {@code chunksToUnload}, so a Keeper teleported
     *       into it is written out and {@code setRemoved(UNLOADED_TO_CHUNK)} by the next
     *       {@code processUnloads()}.</li>
     *   <li>The test's reference then points at a Keeper that has left the level and can never
     *       tick again: "still 78 blocks from its altar after 0 ticks of its own", forever,
     *       whatever the timeout. Which side of that race a run landed on came down to how many
     *       ticks the ticket took to propagate - i.e. to how loaded the machine was.</li>
     * </ul>
     *
     * <p>A {@code ChunkPos} has no y, so the fix is to kite the Keeper <em>up</em> instead: 78
     * blocks straight above the altar is the arena's own chunk, force-loaded and entity-ticking
     * for as long as this test runs, and it is still 78 blocks outside the tether because the
     * tether is a plain 3D distance. Nothing here touches chunk loading.
     *
     * <p>Gravity is then switched off, which is not cosmetic: a falling Keeper drops back inside
     * {@code TETHER_RADIUS} on its own in about 30 ticks, so a test that merely waited for it to
     * come home would pass with the tether deleted. With {@code setNoGravity} it hovers where it
     * was kited until something moves it, and the only thing that can is the tether.
     *
     * <p>The wait below therefore keys off the Keeper's own tick counter rather than off a tick
     * number - nothing depends on how fast the machine runs - and the check itself lands on the
     * first tick the Keeper gets, because {@code Mob#serverAiStep} calls
     * {@code customServerAiStep} on every one of them.
     */
    public static void keeperIsTetheredEvenWhileChasingSomething(GameTestHelper helper) {
        buildRoom(helper);
        ServerLevel level = helper.getLevel();
        BlockPos altar = helper.absolutePos(ALTAR);

        VaultKeeper keeper = VaultFeature.VAULT_KEEPER.get()
                .spawn(level, helper.absolutePos(new BlockPos(2, 1, 2)), MobSpawnType.COMMAND);
        if (keeper == null) {
            helper.fail("could not spawn a Vault Keeper");
            return;
        }
        keeper.bindToAltar(altar);
        keeper.setPersistenceRequired();
        keeper.setNoGravity(true);   // or it simply falls home, tether or no tether - see javadoc

        // Give it a target, exactly as a kiting player would, then drop it well past the tether -
        // straight up the shaft, so it never leaves this arena's chunk. See the javadoc.
        Player chased = helper.makeMockPlayer(GameType.SURVIVAL);
        keeper.setTarget(chased);
        double beyond = VaultKeeper.TETHER_RADIUS + 30.0D;
        keeper.teleportTo(altar.getX() + 0.5D, altar.getY() + 1.0D + beyond, altar.getZ() + 0.5D);

        helper.assertTrue(keeper.getTarget() != null,
                "the test only means anything while the Keeper has a target");
        double kited = Math.sqrt(distanceSqrToAltar(keeper, altar));
        helper.assertTrue(kited > VaultKeeper.TETHER_RADIUS,
                "the Keeper has to start outside the tether; it was only " + Math.round(kited)
                        + " blocks out");
        helper.assertTrue(level.isPositionEntityTicking(keeper.blockPosition()),
                "the kited Keeper must stand somewhere entity-ticking or it is never ticked at "
                        + "all, and this test would be measuring the harness instead of the tether");

        // Baseline, so a failure can tell "the tether never fired" from "the Keeper was never
        // ticked" - which look identical from the outside.
        int ticksWhenKited = keeper.tickCount;

        helper.startSequence()
                // Wait for the Keeper's first tick rather than for a tick number:
                // ServerLevel#tickNonPassenger bumps tickCount and then calls tick(), so a
                // counter that has moved means the whole tick - customServerAiStep included -
                // has already run.
                .thenWaitUntil(() -> {
                    helper.assertTrue(!keeper.isRemoved(),
                            "the Keeper left the level before it ever ticked");
                    helper.assertTrue(keeper.tickCount > ticksWhenKited,
                            "the Keeper has not been ticked once since it was kited, so this run "
                                    + "cannot say anything about the tether yet");
                })
                // One of its own ticks is all the tether ever needs, so it is already home.
                .thenExecute(() -> {
                    int ticks = keeper.tickCount - ticksWhenKited;
                    double distance = Math.sqrt(distanceSqrToAltar(keeper, altar));
                    helper.assertTrue(distance <= VaultKeeper.TETHER_RADIUS,
                            "the Keeper was still " + Math.round(distance)
                                    + " blocks from its altar after " + ticks + " tick(s) of its "
                                    + "own; the tether must fire while it has a target or it can "
                                    + "be kited away");
                    helper.assertTrue(keeper.getTarget() != null,
                            "the Keeper dropped its target before the tether fired - that is the "
                                    + "one case this test exists to cover");
                    keeper.discard();
                })
                .thenSucceed();
    }

    /**
     * A Keeper that is not the one this altar is waiting on must not open the chest when it dies.
     *
     * <p>Any leftover Keeper still carries its {@code altarPos}, so before the UUID check a stray
     * {@code /kill @e[type=creator_vault:vault_keeper]}, a lava death or a Keeper adopted away
     * cracked the chest open mid-fight while the real Keeper stood at full health.
     */
    public static void aStrayKeeperCannotUnsealTheChest(GameTestHelper helper) {
        buildRoom(helper);
        ServerLevel level = helper.getLevel();
        CursedAltarBlockEntity altar = altarAt(helper);
        helper.assertTrue(altar.activate(null), "a sealed altar must accept a key");

        VaultKeeper[] bound = new VaultKeeper[1];
        helper.startSequence()
                .thenWaitUntil(() -> awaitSummonedKeeper(helper, altar, bound))
                .thenExecute(() -> {
                    // A second Keeper that thinks it belongs to this altar - the shape every
                    // orphan takes.
                    VaultKeeper stray = VaultFeature.VAULT_KEEPER.get().spawn(
                            level, helper.absolutePos(new BlockPos(1, 1, 1)), MobSpawnType.COMMAND);
                    if (stray == null) {
                        helper.fail("could not spawn the stray Vault Keeper");
                        return;
                    }
                    stray.bindToAltar(helper.absolutePos(ALTAR));
                    stray.kill();
                })
                // A negative, so it does need a fixed wait: long enough for the altar's 20-tick
                // poll to have run at least once with the stray already dead.
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertBlockPresent(VaultFeature.SEALED_CHEST.get(), CHEST);
                    helper.assertBlockProperty(ALTAR, CursedAltarBlock.STATE, AltarState.ACTIVE);
                    helper.assertTrue(bound[0].isAlive(), "the bound Keeper should be untouched");
                    helper.killAllEntities();
                })
                .thenSucceed();
    }

    /**
     * Handing an altar a second Keeper gets rid of the first one.
     *
     * <p>{@code /vault spawn_keeper} on an already-active altar used to overwrite {@code keeperId}
     * and leave Keeper #1 alive with its own boss bar, its persistence flag and its binding -
     * unreachable by {@code /vault reset}, which only ever discarded the bound one.
     */
    public static void adoptingASecondKeeperRetiresTheFirst(GameTestHelper helper) {
        buildRoom(helper);
        ServerLevel level = helper.getLevel();
        CursedAltarBlockEntity altar = altarAt(helper);

        VaultKeeper first = VaultFeature.VAULT_KEEPER.get()
                .spawn(level, helper.absolutePos(new BlockPos(2, 1, 2)), MobSpawnType.COMMAND);
        VaultKeeper second = VaultFeature.VAULT_KEEPER.get()
                .spawn(level, helper.absolutePos(new BlockPos(6, 1, 2)), MobSpawnType.COMMAND);
        if (first == null || second == null) {
            helper.fail("could not spawn both Vault Keepers");
            return;
        }
        helper.assertTrue(altar.adoptKeeper(level, first), "the altar must adopt the first Keeper");
        helper.assertTrue(altar.adoptKeeper(level, second), "the altar must adopt the second too");

        helper.assertTrue(first.isRemoved(),
                "the first Keeper must be discarded, not orphaned with its boss bar still up");
        helper.assertTrue(second.getUUID().equals(altar.keeperId()),
                "the altar must be bound to the Keeper it just adopted");
        helper.assertTrue(second.isAlive(), "the second Keeper must survive the swap");
        helper.killAllEntities();
        helper.succeed();
    }

    /**
     * {@code /vault reset} must clear a Keeper that is bound to the altar even when the altar has
     * forgotten it - the shape a reset run while the Keeper's chunk was out used to leave behind.
     */
    public static void resetSweepsAKeeperTheAltarHasForgotten(GameTestHelper helper) {
        buildRoom(helper);
        ServerLevel level = helper.getLevel();
        CursedAltarBlockEntity altar = altarAt(helper);

        VaultKeeper orphan = VaultFeature.VAULT_KEEPER.get()
                .spawn(level, helper.absolutePos(new BlockPos(2, 1, 2)), MobSpawnType.COMMAND);
        if (orphan == null) {
            helper.fail("could not spawn a Vault Keeper");
            return;
        }
        // Bound to the altar, but the altar knows nothing about it: keeperId is still null.
        orphan.bindToAltar(helper.absolutePos(ALTAR));
        orphan.setPersistenceRequired();
        helper.assertTrue(altar.keeperId() == null, "the altar should not know this Keeper");

        altar.reset(level);

        helper.assertTrue(orphan.isRemoved(),
                "reset must sweep every Keeper still pointing at this altar, not only the bound one");
        helper.killAllEntities();
        helper.succeed();
    }

    /**
     * The documented recovery path for an altar mined by accident: {@code /vault unseal} still
     * opens the chests it left behind.
     *
     * <p>A Sealed Chest is unbreakable, unopenable, unpushable and is not a container, so without
     * this the chests are bricked permanently - and mining an altar in creative while dressing a
     * set is very easy to do.
     */
    public static void unsealWithoutAnAltarStillOpensTheChests(GameTestHelper helper) {
        // Deliberately no altar in this arena.
        helper.setBlock(CHEST, VaultFeature.SEALED_CHEST.get().defaultBlockState()
                .setValue(SealedChestBlock.FACING, Direction.NORTH));
        ServerLevel level = helper.getLevel();

        int opened = SealedChestBlock.unsealAround(level, helper.absolutePos(CHEST), 4, 0L);
        helper.assertTrue(opened >= 1, "expected the orphaned chest to open, opened " + opened);
        helper.assertBlockPresent(Blocks.CHEST, CHEST);

        ChestBlockEntity chest = helper.getBlockEntity(CHEST);
        if (chest == null) {
            helper.fail("unsealing did not leave a ChestBlockEntity", CHEST);
            return;
        }
        helper.assertTrue(VaultFeature.CHEST_LOOT_TABLE.equals(chest.getLootTable()),
                "an orphaned chest must still carry the vault loot table");
        helper.succeed();
    }

    /**
     * A Sealed Chest dressed with its own loot table in the structure NBT keeps that table across
     * {@code /vault reset} - i.e. between takes, which is the only time reset ever runs.
     */
    public static void resetKeepsAPerChestLootTableOverride(GameTestHelper helper) {
        buildRoom(helper);
        ServerLevel level = helper.getLevel();
        CursedAltarBlockEntity altar = altarAt(helper);

        SealedChestBlockEntity sealed = helper.getBlockEntity(CHEST);
        if (sealed == null) {
            helper.fail("no SealedChestBlockEntity was created", CHEST);
            return;
        }
        sealed.setLootTable(VaultFeature.TRAP_LOOT_TABLE);

        helper.assertTrue(altar.activate(null), "a sealed altar must accept a key");
        altar.onKeeperDead(level);
        ChestBlockEntity opened = helper.getBlockEntity(CHEST);
        if (opened == null) {
            helper.fail("unsealing did not leave a ChestBlockEntity", CHEST);
            return;
        }
        helper.assertTrue(VaultFeature.TRAP_LOOT_TABLE.equals(opened.getLootTable()),
                "unsealing must honour the per-chest table, got " + opened.getLootTable());

        altar.reset(level);
        helper.assertBlockPresent(VaultFeature.SEALED_CHEST.get(), CHEST);
        SealedChestBlockEntity resealed = helper.getBlockEntity(CHEST);
        if (resealed == null) {
            helper.fail("re-sealing did not leave a SealedChestBlockEntity", CHEST);
            return;
        }
        helper.assertTrue(VaultFeature.TRAP_LOOT_TABLE.equals(resealed.lootTable()),
                "the re-sealed chest reverted to " + resealed.lootTable()
                        + " - a custom-loot chest must survive a reset");
        helper.killAllEntities();
        helper.succeed();
    }

    // ------------------------------------------------------------------ setup

    /**
     * Sequence step that waits for the altar to finish its charge and hand over its Keeper, parking
     * it in {@code out[0]}.
     *
     * <p>A wait rather than the {@code runAtTickTime(80, ...)} these tests used to do. Sampling
     * once at a fixed tick asserts a schedule the test does not own: it fails outright if the
     * charge is ever retuned, and it reads as flakiness rather than as the off-by-one it is. The
     * only thing worth asserting here is that the Keeper does turn up, so this retries every tick
     * and the test's own {@code timeoutTicks} is the deadline.
     */
    private static void awaitSummonedKeeper(GameTestHelper helper, CursedAltarBlockEntity altar,
                                            VaultKeeper[] out) {
        out[0] = altar.resolveKeeper(helper.getLevel());
        helper.assertTrue(out[0] != null, "the altar has not produced its Vault Keeper yet (its "
                + "charge is " + AltarStateMachine.CHARGE_TICKS + " ticks)");
    }

    /** Distance squared from the Keeper to the centre of the altar block it is bound to. */
    private static double distanceSqrToAltar(VaultKeeper keeper, BlockPos altar) {
        return keeper.distanceToSqr(altar.getX() + 0.5D, altar.getY() + 0.5D, altar.getZ() + 0.5D);
    }

    private static void buildRoom(GameTestHelper helper) {
        helper.setBlock(ALTAR, VaultFeature.CURSED_ALTAR.get());
        helper.setBlock(CHEST, VaultFeature.SEALED_CHEST.get().defaultBlockState()
                .setValue(SealedChestBlock.FACING, Direction.NORTH));
    }

    private static CursedAltarBlockEntity altarAt(GameTestHelper helper) {
        CursedAltarBlockEntity altar = helper.getBlockEntity(ALTAR);
        if (altar == null) {
            helper.fail("no CursedAltarBlockEntity was created", ALTAR);
            throw new IllegalStateException("unreachable: helper.fail throws");
        }
        return altar;
    }

    /**
     * Asserts the altar wired itself to <em>this</em> arena's chest. Deliberately not a count:
     * see the class javadoc on neighbouring test arenas.
     */
    private static void assertChestRecorded(GameTestHelper helper, CursedAltarBlockEntity altar,
                                            BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        for (CursedAltarBlockEntity.StoredChest chest : altar.sealedChests()) {
            if (chest.pos().equals(absolute)) {
                return;
            }
        }
        helper.fail("the altar did not record the Sealed Chest at " + relative
                + " (recorded " + altar.sealedChests() + ")", relative);
    }

    private VaultGameTests() {
    }
}
