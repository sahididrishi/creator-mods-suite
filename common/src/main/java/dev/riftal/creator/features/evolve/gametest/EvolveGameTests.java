package dev.riftal.creator.features.evolve.gametest;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.data.EvolutionData;
import dev.riftal.creator.features.evolve.entity.ApexBeast;
import dev.riftal.creator.features.evolve.event.EvolveServerHooks;
import dev.riftal.creator.features.evolve.net.XpPopupPayload;
import dev.riftal.creator.features.evolve.perk.StagePerks;
import dev.riftal.creator.features.evolve.progression.EvolveManager;
import dev.riftal.creator.features.evolve.progression.Transformation;
import dev.riftal.creator.features.evolve.stage.EvolutionStage;
import dev.riftal.creator.features.evolve.stage.StageModifiers;
import dev.riftal.creator.features.evolve.stage.Stages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * GameTest bodies for the {@code evolve} feature. Vanilla API only, so both loaders can call them.
 *
 * <p>Add a {@code public static void name(GameTestHelper helper)} here, then one annotated stub in
 * {@code fabric/src/gametest/java/.../EvolveFabricGameTests.java} and one in
 * {@code neoforge/src/main/java/.../EvolveNeoForgeGameTests.java}.
 *
 * <p>These all use {@link GameTestHelper#makeMockPlayer(GameType)} rather than
 * {@code makeMockServerPlayerInLevel()}: the mock player is enough to exercise the attribute
 * modifiers and the attachment, and it is not pushed into the server's player list, so it cannot
 * leak into a later test in the same batch.
 */
public final class EvolveGameTests {

    private static final double EPSILON = 1.0E-4D;

    /** Smoke test: the feature survived the config filter and is live in this session. */
    public static void featureIsEnabled(GameTestHelper helper) {
        helper.assertTrue(CreatorMods.isEnabled(EvolveFeature.ID),
                "feature '" + EvolveFeature.ID + "' should be enabled in the test session");
        helper.assertTrue(EvolveFeature.isReady(),
                "registerContent() should have run and bound the evolution attachment");
        helper.succeed();
    }

    /** Stage 3 writes the Brute body: 1.25x scale, 26 max health, and a longer reach is untouched. */
    public static void brutePutsOnTheRightBody(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        StageModifiers.apply(player, Stages.BRUTE);
        assertClose(helper, player.getAttributeValue(Attributes.SCALE), 1.25D, "scale at Brute");
        assertClose(helper, player.getAttributeValue(Attributes.MAX_HEALTH), 26.0D, "max health at Brute");
        assertClose(helper, player.getAttributeValue(Attributes.ATTACK_DAMAGE), 1.5D, "attack at Brute");
        helper.assertTrue(player.getAttribute(Attributes.SCALE).hasModifier(StageModifiers.SCALE_ID),
                "the scale modifier should be present under its fixed id");

        // Applying twice must not stack.
        StageModifiers.apply(player, Stages.BRUTE);
        assertClose(helper, player.getAttributeValue(Attributes.SCALE), 1.25D, "scale after a second apply");

        StageModifiers.clear(player);
        assertClose(helper, player.getAttributeValue(Attributes.SCALE), 1.0D, "scale after clear");
        assertClose(helper, player.getAttributeValue(Attributes.MAX_HEALTH), 20.0D, "max health after clear");
        helper.assertFalse(player.getAttribute(Attributes.SCALE).hasModifier(StageModifiers.SCALE_ID),
                "clear() should remove the scale modifier");
        helper.succeed();
    }

    /** Stage 4 is the one that has to walk up two-block steps and jump three blocks. */
    public static void titanGetsStepHeightAndJump(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        StageModifiers.apply(player, Stages.TITAN);
        assertClose(helper, player.getAttributeValue(Attributes.STEP_HEIGHT), 2.0D, "Titan step height");
        assertClose(helper, player.getAttributeValue(Attributes.JUMP_STRENGTH), 0.62D, "Titan jump strength");
        assertClose(helper, player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE), 5.0D, "Titan safe fall");
        assertClose(helper, player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), 6.0D,
                "Titan block reach");
        assertClose(helper, player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), 5.0D,
                "Titan entity reach");

        StageModifiers.clear(player);
        assertClose(helper, player.getAttributeValue(Attributes.STEP_HEIGHT), 0.6D, "step height after clear");
        helper.succeed();
    }

    /** The transformation lock pins movement speed at zero, and releases cleanly. */
    public static void transformLockStopsAndReleases(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        double base = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(base > 0.0D, "a fresh player should have some movement speed");

        StageModifiers.lockMovement(player);
        helper.assertTrue(StageModifiers.isMovementLocked(player), "the lock should report as on");
        assertClose(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.0D, "locked movement speed");

        // The lock must be idempotent - a relog mid-sequence re-applies it.
        StageModifiers.lockMovement(player);
        assertClose(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.0D, "movement speed after re-lock");

        StageModifiers.unlockMovement(player);
        helper.assertFalse(StageModifiers.isMovementLocked(player), "the lock should report as off");
        assertClose(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base, "movement speed after unlock");
        helper.succeed();
    }

    /**
     * The attachment is registered by the loader and round-trips. This is the half of
     * {@code EvolutionData} that plain JUnit cannot reach, because the attachment plumbing is
     * loader-specific.
     */
    public static void attachmentRoundTrips(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.assertValueEqual(EvolveFeature.data().get(player), EvolutionData.INITIAL,
                "a fresh player's evolution data");

        EvolutionData written = new EvolutionData(4, 912, 31, false, 0L, EvolutionData.MODEL_FORCED_OFF);
        EvolveFeature.data().set(player, written);
        helper.assertValueEqual(EvolveFeature.data().get(player), written, "stored evolution data");

        EvolutionData updated = EvolveFeature.data().update(player, current -> current.withXp(1_000));
        helper.assertValueEqual(updated.xp(), 1_000, "xp after update()");
        helper.assertValueEqual(EvolveFeature.data().get(player).stage(), 4, "stage survived the update");
        helper.succeed();
    }

    /**
     * The Apex beast entity type is registered with attributes and can be spawned - which is what
     * proves {@code EntityAttributes.register} reached the loader. Spawning it is also how the
     * human gets a beast on camera without being stage 5.
     */
    public static void apexBeastSpawnsWithItsAttributes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        ApexBeast beast = helper.spawn(EvolveFeature.apexBeast().get(), pos);

        helper.assertTrue(beast != null, "the apex beast should spawn");
        assertClose(helper, beast.getAttributeValue(Attributes.MAX_HEALTH), 80.0D, "apex beast max health");
        assertClose(helper, beast.getAttributeValue(Attributes.ATTACK_DAMAGE), 10.0D, "apex beast attack");
        helper.assertEntityPresent(EvolveFeature.apexBeast().get(), pos, 3.0D);

        helper.killAllEntities();
        helper.succeed();
    }

    /**
     * Every rung of the ladder resizes the body, not just the number: the scale attribute, the
     * hitbox and the eye height all move together, and {@code clear()} puts a vanilla player back.
     * This is the assertion the camera cares about - a Titan whose hitbox did not grow walks
     * through its own shoulders.
     */
    public static void everyStageResizesThePlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        for (EvolutionStage stage : Stages.ALL) {
            StageModifiers.apply(player, stage);
            double scale = stage.scaleMultiplier();

            assertClose(helper, player.getAttributeValue(Attributes.SCALE), scale,
                    stage.key() + " scale attribute");
            assertClose(helper, player.getScale(), scale, stage.key() + " entity scale");
            assertClose(helper, player.getBbHeight(), 1.8D * scale, stage.key() + " hitbox height");
            assertClose(helper, player.getBbWidth(), 0.6D * scale, stage.key() + " hitbox width");
            assertClose(helper, player.getEyeHeight(), 1.62D * scale, stage.key() + " eye height");
        }

        StageModifiers.clear(player);
        assertClose(helper, player.getScale(), 1.0D, "scale after clear");
        assertClose(helper, player.getBbHeight(), 1.8D, "hitbox height after clear");
        assertClose(helper, player.getEyeHeight(), 1.62D, "eye height after clear");
        helper.succeed();
    }

    /**
     * Shrinking out of a big stage must not leave the player on more health than they can hold -
     * vanilla would keep 34/16 hp and the health bar would be nonsense until the next damage tick.
     */
    public static void shrinkingClampsHealthIntoTheNewMaximum(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        StageModifiers.apply(player, Stages.TITAN);
        player.setHealth(player.getMaxHealth());
        assertClose(helper, player.getHealth(), 35.0D, "health at Titan");

        StageModifiers.apply(player, Stages.HATCHLING);
        assertClose(helper, player.getMaxHealth(), 16.0D, "max health at Hatchling");
        assertClose(helper, player.getHealth(), 16.0D, "health should be clamped, not left at 35");

        // Growing again must not silently heal.
        StageModifiers.apply(player, Stages.APEX);
        assertClose(helper, player.getMaxHealth(), 50.0D, "max health at Apex");
        assertClose(helper, player.getHealth(), 16.0D, "growing is not a heal");
        helper.succeed();
    }

    /**
     * The ids in code and the ids in the resource pack have to be the same string. A typo here is
     * invisible until the roar is silent on camera.
     */
    public static void registeredIdsMatchTheShippedAssets(GameTestHelper helper) {
        helper.assertValueEqual(BuiltInRegistries.SOUND_EVENT.getKey(EvolveFeature.roarSmall()),
                EvolveFeature.id("evolve.roar_small"), "roar_small sound id");
        helper.assertValueEqual(BuiltInRegistries.SOUND_EVENT.getKey(EvolveFeature.roarApex()),
                EvolveFeature.id("evolve.roar_apex"), "roar_apex sound id");
        helper.assertValueEqual(BuiltInRegistries.SOUND_EVENT.getKey(EvolveFeature.evolveComplete()),
                EvolveFeature.id("evolve.complete"), "complete sound id");
        helper.assertValueEqual(BuiltInRegistries.ENTITY_TYPE.getKey(EvolveFeature.apexBeast().get()),
                EvolveFeature.id("apex_beast"), "apex beast entity id");
        helper.assertValueEqual(EvolveFeature.apexBeast().get().getDescriptionId(),
                "entity.creator_evolve.apex_beast", "apex beast translation key");
        helper.succeed();
    }

    /**
     * The headline loop: kill something, earn XP, cross a threshold, and come out of the sequence
     * wearing the next body. Runs end to end through the {@code die} mixin, the manager, the
     * scheduler and the attribute modifiers.
     */
    public static void killXpAutoEvolvesAndTheSequenceLands(GameTestHelper helper) {
        ServerPlayer player = spawnPlayer(helper, new Vec3(4.5D, 2.0D, 4.5D));
        EvolveManager.setStage(player, 1, true);
        EvolveManager.addXp(player, 90, XpPopupPayload.SOURCE_COMMAND);
        helper.assertValueEqual(EvolveFeature.data().get(player).xp(), 90, "xp before the kill");

        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        zombie.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0F);

        EvolutionData afterKill = EvolveFeature.data().get(player);
        helper.assertValueEqual(afterKill.xp(), 105, "xp after killing a zombie");
        helper.assertValueEqual(afterKill.totalKills(), 1, "kill counter");
        helper.assertValueEqual(afterKill.stage(), 2, "the stage is banked as the sequence starts");
        helper.assertTrue(afterKill.transforming(), "the transformation should be running");
        helper.assertTrue(StageModifiers.isMovementLocked(player),
                "a transforming player cannot walk out of shot");

        helper.runAfterDelay(Transformation.TICKS + 10L, () -> {
            EvolutionData landed = EvolveFeature.data().get(player);
            helper.assertFalse(landed.transforming(), "the sequence should have finished");
            helper.assertValueEqual(landed.stage(), 2, "stage after the sequence");
            assertClose(helper, player.getScale(), Stages.RUNT.scaleMultiplier(), "scale at Runt");
            helper.assertFalse(StageModifiers.isMovementLocked(player),
                    "the movement lock must be released");
            release(helper, player);
            helper.succeed();
        });
    }

    /** A mob that dies to something other than a player feeds nobody's ladder. */
    public static void aKillWithNoKillerAwardsNothing(GameTestHelper helper) {
        ServerPlayer player = spawnPlayer(helper, new Vec3(4.5D, 2.0D, 4.5D));
        EvolveManager.setStage(player, 3, true);
        int before = EvolveFeature.data().get(player).xp();

        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        zombie.hurt(helper.getLevel().damageSources().genericKill(), 1000.0F);

        EvolutionData after = EvolveFeature.data().get(player);
        helper.assertValueEqual(after.xp(), before, "xp must not move for a kill we did not make");
        helper.assertValueEqual(after.totalKills(), 0, "kill counter must not move either");
        helper.assertFalse(after.transforming(), "and nothing should be transforming");

        release(helper, player);
        helper.succeed();
    }

    /** Eating is the quiet XP source: bread is nutrition 5, so it is worth ten. */
    public static void eatingBreadFeedsTheLadder(GameTestHelper helper) {
        ServerPlayer player = spawnPlayer(helper, new Vec3(4.5D, 2.0D, 4.5D));
        EvolveManager.setStage(player, 1, true);

        ItemStack bread = new ItemStack(Items.BREAD);
        FoodProperties food = bread.get(DataComponents.FOOD);
        helper.assertTrue(food != null, "bread should carry a food component");
        helper.assertValueEqual(food.nutrition(), 5, "bread nutrition");

        player.eat(helper.getLevel(), bread, food);

        helper.assertValueEqual(EvolveFeature.data().get(player).xp(), 10,
                "nutrition 5 should be worth ten evolution xp");
        release(helper, player);
        helper.succeed();
    }

    /** {@code /evolve reset} has to leave nothing behind - that is what the next take depends on. */
    public static void resetUndoesEverything(GameTestHelper helper) {
        ServerPlayer player = spawnPlayer(helper, new Vec3(4.5D, 2.0D, 4.5D));

        EvolveManager.setStage(player, 5, true);
        EvolveManager.setModelOverride(player, EvolutionData.MODEL_FORCED_ON);
        assertClose(helper, player.getScale(), Stages.APEX.scaleMultiplier(), "scale at Apex");
        helper.assertValueEqual(EvolveFeature.data().get(player).stage(), 5, "stage after set 5");

        EvolveManager.reset(player);

        EvolutionData after = EvolveFeature.data().get(player);
        helper.assertValueEqual(after.stage(), 1, "stage after reset");
        helper.assertValueEqual(after.xp(), 0, "xp after reset");
        helper.assertValueEqual(after.modelOverride(), EvolutionData.MODEL_AUTO,
                "the model override must be released too");
        helper.assertFalse(after.usesBeastModel(), "a Hatchling is not a beast");
        assertClose(helper, player.getScale(), Stages.HATCHLING.scaleMultiplier(), "scale after reset");
        assertClose(helper, player.getHealth(), 16.0D, "reset heals into the Hatchling maximum");
        helper.assertFalse(StageModifiers.isMovementLocked(player), "no lock may survive a reset");

        release(helper, player);
        helper.succeed();
    }

    /**
     * A big {@code /evolve xp} jumps several rungs at once. It must run <em>one</em> sequence
     * straight to the top rather than five back to back, and the beast at the end is a render swap
     * on the client - no entity is ever spawned server side.
     */
    public static void multiStageJumpLandsOnApexWithoutSpawningABeast(GameTestHelper helper) {
        ServerPlayer player = spawnPlayer(helper, new Vec3(4.5D, 2.0D, 4.5D));
        EvolveManager.setStage(player, 1, true);

        EvolveManager.addXp(player, 5_000, XpPopupPayload.SOURCE_COMMAND);

        EvolutionData banked = EvolveFeature.data().get(player);
        helper.assertValueEqual(banked.stage(), Stages.MAX, "one sequence, straight to Apex");
        helper.assertTrue(banked.transforming(), "and it should be running");

        helper.runAfterDelay(Transformation.APEX_TICKS + 10L, () -> {
            EvolutionData landed = EvolveFeature.data().get(player);
            helper.assertFalse(landed.transforming(), "the Apex sequence should have finished");
            helper.assertValueEqual(landed.stage(), Stages.MAX, "final stage");
            helper.assertTrue(landed.usesBeastModel(), "Apex wears the beast");
            assertClose(helper, player.getScale(), Stages.APEX.scaleMultiplier(), "scale at Apex");
            assertClose(helper, player.getAttributeValue(Attributes.MAX_HEALTH), 50.0D,
                    "max health at Apex");
            helper.assertEntityNotPresent(EvolveFeature.apexBeast().get());
            release(helper, player);
            helper.succeed();
        });
    }

    /**
     * A mock {@code ServerPlayer} placed in the test level. It goes into the server's player list,
     * so every test that takes one hands it back to {@link #release}.
     */
    private static ServerPlayer spawnPlayer(GameTestHelper helper, Vec3 relativePos) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = helper.absoluteVec(relativePos);
        player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        return player;
    }

    /** Undoes {@link #spawnPlayer}: clears our state off the player and drops it from the list. */
    private static void release(GameTestHelper helper, ServerPlayer player) {
        Transformation.cancelTasks(player.getUUID());
        StageModifiers.unlockMovement(player);
        StagePerks.revokeAll(player);
        EvolveServerHooks.forgetAll();
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static void assertClose(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) < EPSILON,
                what + ": expected " + expected + " but was " + actual);
    }

    private EvolveGameTests() {
    }
}
