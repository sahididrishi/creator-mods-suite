package dev.riftal.creator.features.toolkit.gametest;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.toolkit.ToolkitDimensions;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import dev.riftal.creator.features.toolkit.ToolkitRuntime;
import dev.riftal.creator.features.toolkit.ToolkitState;
import dev.riftal.creator.features.toolkit.arena.ArenaManager;
import dev.riftal.creator.features.toolkit.cam.CameraBookmark;
import dev.riftal.creator.features.toolkit.freeze.FreezeManager;
import dev.riftal.creator.features.toolkit.take.Mark;
import dev.riftal.creator.features.toolkit.take.TakeLog;
import dev.riftal.creator.features.toolkit.take.TakeManager;
import dev.riftal.creator.features.toolkit.take.TakeState;
import dev.riftal.creator.features.toolkit.wave.WaveMath;
import dev.riftal.creator.features.toolkit.wave.WaveSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * GameTest bodies for the {@code toolkit} feature. Vanilla API only, so both loaders can call them.
 *
 * <p>Add a {@code public static void name(GameTestHelper helper)} here, then one annotated stub in
 * {@code fabric/src/gametest/java/.../ToolkitFabricGameTests.java} and one in
 * {@code neoforge/src/main/java/.../ToolkitNeoForgeGameTests.java}.
 *
 * <p>Tests in a batch tick side by side, and three of this feature's systems are server-wide
 * singletons (the take recorder, the freeze flags, the wave tag). So there is exactly <em>one</em>
 * test per singleton, each covering that system end to end, rather than several tests fighting over
 * the same static. Nothing here schedules new work from inside a scheduled callback either -
 * {@code GameTestInfo} iterates that map while it runs it - so multi-phase tests use a sequence.
 */
public final class ToolkitGameTests {

    /** Smoke test: the feature survived the config filter and is live in this session. */
    public static void featureIsEnabled(GameTestHelper helper) {
        helper.assertTrue(CreatorMods.isEnabled(ToolkitFeature.ID),
                "feature '" + ToolkitFeature.ID + "' should be enabled in the test session");
        helper.succeed();
    }

    /**
     * {@code /toolkit wave spawn zombie 8 2 ring} puts exactly eight tagged zombies on the floor,
     * every one of them on the circle and facing its centre, and {@code wave clear} takes those
     * eight away without touching anything else that happens to be standing there.
     */
    public static void waveRingSpawnsExactCount(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 centre = helper.absoluteVec(new Vec3(4.5D, 2.0D, 4.5D));
        double radius = 2.0D;

        // A bystander: not spawned by the toolkit, so /toolkit wave clear must leave it alone.
        Zombie bystander = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));

        int spawned = WaveSpawner.spawn(level, centre, EntityType.ZOMBIE, 8, radius, WaveMath.Mode.RING);
        helper.assertTrue(spawned == 8, "expected 8 zombies, spawned " + spawned);

        int tagged = 0;
        for (Zombie zombie : helper.getEntities(EntityType.ZOMBIE)) {
            if (!zombie.getTags().contains(WaveSpawner.WAVE_TAG)) {
                continue;
            }
            tagged++;
            // Block-centred placement can shift a mob half a block off the mathematical circle.
            double dx = zombie.getX() - centre.x;
            double dz = zombie.getZ() - centre.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            helper.assertTrue(Math.abs(distance - radius) < 1.0D,
                    "ring zombie is " + distance + " from the centre, wanted " + radius);
            // Facing the centre: the look vector and the vector to the centre point the same way.
            double lookX = -Math.sin(Math.toRadians(zombie.getYRot()));
            double lookZ = Math.cos(Math.toRadians(zombie.getYRot()));
            double dot = (-dx * lookX - dz * lookZ) / Math.max(1.0E-6D, distance);
            helper.assertTrue(dot > 0.9D, "ring zombie faces away from the centre (dot " + dot + ")");
        }
        helper.assertTrue(tagged == 8, "expected 8 tagged wave zombies, found " + tagged);

        int cleared = WaveSpawner.clear(level);
        helper.assertTrue(cleared >= 8, "wave clear removed " + cleared + " entities");
        helper.assertFalse(bystander.isRemoved(), "wave clear discarded an untagged entity");

        bystander.discard();
        helper.succeed();
    }

    /**
     * A frozen mob does not move, does not fall and is not removed - and the moment the freeze is
     * lifted it falls again, with nothing left behind on the entity to undo.
     */
    public static void freezeMobsStopsMovement(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 5, 4));
        zombie.setNoGravity(false);
        FreezeManager.setMobs(server, true);

        // Index 0: where the zombie was once the freeze was definitely in force.
        Vec3[] frozenAt = new Vec3[1];

        helper.startSequence()
                .thenExecuteAfter(2, () -> frozenAt[0] = zombie.position())
                .thenExecuteAfter(20, () -> {
                    Vec3 now = zombie.position();
                    boolean removed = zombie.isRemoved();
                    // Release first: a failing assertion must not leave the world frozen.
                    FreezeManager.setMobs(server, false);
                    helper.assertFalse(removed, "a frozen zombie was removed");
                    helper.assertTrue(now.distanceToSqr(frozenAt[0]) < 1.0E-4D,
                            "a frozen zombie moved from " + frozenAt[0] + " to " + now);
                })
                .thenExecuteAfter(20, () -> {
                    Vec3 now = zombie.position();
                    helper.assertTrue(now.y < frozenAt[0].y - 0.5D,
                            "a released zombie should fall: y went " + frozenAt[0].y + " -> " + now.y);
                    helper.assertFalse(FreezeManager.mobsFrozen(), "the freeze flag should be off");
                    zombie.discard();
                })
                .thenSucceed();
    }

    /**
     * An arena snapshot puts a broken block back exactly where it was, and re-creates the actor that
     * was standing in the box, name and all.
     */
    public static void arenaResetRestoresBlocksAndEntities(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos marker = new BlockPos(2, 1, 2);
        helper.setBlock(marker, Blocks.GOLD_BLOCK);

        Zombie actor = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        actor.setCustomName(Component.literal("A"));
        actor.setPersistenceRequired();

        ArenaManager.SaveResult saved = ArenaManager.save(level, "gametest",
                helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(3, 3, 3)));
        helper.assertTrue(saved.ok(), "arena save failed: " + saved.error());
        helper.assertTrue(saved.snapshot().entities().size() == 1,
                "the actor should have been captured, got " + saved.snapshot().entities().size());
        helper.assertTrue(saved.snapshot().volume() == 27L,
                "a 3x3x3 box is 27 blocks, not " + saved.snapshot().volume());

        // Wreck the set: break the marker and kill the actor.
        helper.setBlock(marker, Blocks.AIR);
        actor.discard();
        helper.assertBlockNotPresent(Blocks.GOLD_BLOCK, marker);

        ArenaManager.ResetResult result = ArenaManager.reset(level, saved.snapshot());
        helper.assertBlockPresent(Blocks.GOLD_BLOCK, marker);
        helper.assertTrue(result.restored() == 1,
                "expected 1 entity restored, got " + result.restored());

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    int named = 0;
                    for (Zombie zombie : helper.getEntities(EntityType.ZOMBIE)) {
                        Component name = zombie.getCustomName();
                        if (name != null && "A".equals(name.getString())) {
                            named++;
                            zombie.discard();
                        }
                    }
                    helper.assertTrue(named == 1, "expected the named actor back, found " + named);
                })
                .thenSucceed();
    }

    /** A box bigger than the cap is refused outright, before a single block is read. */
    public static void arenaSaveRejectsOversizeVolumes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));

        ArenaManager.SaveResult tooBig = ArenaManager.save(level, "toobig", origin,
                origin.offset(80, 80, 80));
        helper.assertFalse(tooBig.ok(), "a 531441-block arena should have been refused");
        helper.assertTrue(tooBig.error() != null
                        && tooBig.error().contains(String.valueOf(ArenaManager.MAX_VOLUME)),
                "the refusal should name the limit, said: " + tooBig.error());

        ArenaManager.SaveResult small = ArenaManager.save(level, "small", origin, origin.offset(1, 1, 1));
        helper.assertTrue(small.ok(), "a 2x2x2 arena should save: " + small.error());
        helper.succeed();
    }

    /**
     * Start, two marks, stop. The take number comes from the world's saved state, the marks are
     * numbered from one, and the log file on disk carries a line per event.
     */
    public static void takeRecordsMarks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ToolkitRuntime.bind(server);
        TakeManager.reset();
        TakeManager.setNextNumber(server, 42);

        TakeState started = TakeManager.start(server);
        helper.assertTrue(started != null, "take did not start");
        helper.assertTrue(started.number() == 42,
                "take number should come from /toolkit take set, got " + started.number());
        helper.assertTrue(TakeManager.start(server) == null,
                "a second start must be refused while a take is running");

        Mark first = TakeManager.mark(server, "gametest", "one");
        Mark second = TakeManager.mark(server, "gametest", "two");
        helper.assertTrue(first != null && first.index() == 1, "first mark refused or misnumbered");
        helper.assertTrue(second != null && second.index() == 2, "second mark refused or misnumbered");

        TakeLog log = TakeManager.log();
        helper.assertTrue(log != null, "no take log was opened");
        helper.assertTrue(log.fileName().endsWith("take-042.log"),
                "log file is named " + log.fileName());

        TakeState stopped = TakeManager.stop(server);
        helper.assertTrue(stopped != null, "take did not stop");
        helper.assertFalse(stopped.running(), "take is still running after stop");
        helper.assertTrue(stopped.marks().size() == 2,
                "expected 2 marks, got " + stopped.marks().size());
        helper.assertTrue(TakeManager.stop(server) == null, "a second stop must be refused");
        helper.assertTrue(TakeManager.nextNumber(server) == 43,
                "the next take should be 43, is " + TakeManager.nextNumber(server));

        try {
            List<String> lines = Files.readAllLines(log.file());
            helper.assertTrue(lines.size() == 4,
                    "header + 2 marks + footer is 4 lines, wrote " + lines.size());
            helper.assertTrue(lines.get(0).startsWith("take=42 "), lines.get(0));
            helper.assertTrue(lines.get(1).startsWith("mark=1 "), lines.get(1));
            helper.assertTrue(lines.get(3).startsWith("stop="), lines.get(3));
        } catch (IOException e) {
            helper.fail("take log was not readable: " + e);
        }

        TakeManager.reset();
        helper.succeed();
    }

    /**
     * Camera bookmarks live in the world's saved state, which is what makes the shot list shared by
     * the whole crew; names are matched case-insensitively so {@code /toolkit cam go hero} finds the
     * one saved as {@code Hero}.
     */
    public static void cameraBookmarksRoundTripThroughSavedState(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ToolkitRuntime.bind(server);
        ToolkitState state = ToolkitState.get(server);
        ResourceLocation dimension = helper.getLevel().dimension().location();

        CameraBookmark hero = new CameraBookmark("Hero", dimension,
                12.5D, 68.0D, -40.5D, 134.5F, -22.5F);
        state.putCamera(hero);

        CameraBookmark found = state.camera("hero");
        helper.assertTrue(found != null, "a camera saved as 'Hero' must be found as 'hero'");
        helper.assertTrue(hero.equals(found), "the stored bookmark changed: " + found);
        helper.assertTrue(ToolkitDimensions.level(server, found.dimension()) != null,
                "the bookmark's dimension should resolve back to a live level");

        CameraBookmark reloaded = CameraBookmark.load(found.save());
        helper.assertTrue(hero.equals(reloaded), "the bookmark did not survive its own NBT");

        helper.assertTrue(state.removeCamera("HERO"), "deleting by a differently-cased name failed");
        helper.assertTrue(state.camera("hero") == null, "the camera should be gone");
        helper.succeed();
    }

    private ToolkitGameTests() {
    }
}
