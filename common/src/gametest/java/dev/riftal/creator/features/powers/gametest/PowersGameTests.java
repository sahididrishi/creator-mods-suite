package dev.riftal.creator.features.powers.gametest;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.powers.PowersFeature;
import dev.riftal.creator.features.powers.ability.Ability;
import dev.riftal.creator.features.powers.ability.AbilityRegistry;
import dev.riftal.creator.features.powers.ability.UseResult;
import dev.riftal.creator.features.powers.data.PlayerPowers;
import dev.riftal.creator.features.powers.effect.ActiveEffects;
import dev.riftal.creator.features.powers.server.PowerManager;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * GameTest bodies for the {@code powers} feature. Vanilla API only, so both loaders can call them.
 *
 * <p>Add a {@code public static void name(GameTestHelper helper)} here, then one annotated stub in
 * {@code fabric/src/gametest/java/.../PowersFabricGameTests.java} and one in
 * {@code neoforge/src/gametest/java/.../PowersNeoForgeGameTests.java}.
 *
 * <h2>The test player</h2>
 * Abilities take a {@link ServerPlayer} and {@code GameTestHelper#makeMockPlayer} only hands out a
 * plain {@code Player}, so these tests use {@code GameTestHelper#makeMockServerPlayerInLevel()}:
 * it puts a {@code Connection} on an {@code EmbeddedChannel} and runs the player through
 * {@code PlayerList#placeNewPlayer}, which is what leaves {@code player.connection} non-null.
 *
 * <p>That connection is not optional, and an earlier revision of this file was wrong to build a
 * detached {@code ServerPlayer} and claim the null connection was harmless. Ordinary vanilla API
 * talks to it with no null check: {@code LivingEntity#addEffect} routes through
 * {@code ServerPlayer#onEffectAdded}, whose second line is
 * {@code this.connection.send(new ClientboundUpdateMobEffectPacket(...))}. A detached player
 * therefore cannot survive Shield Dome handing out Resistance II. The feature's <em>own</em> S2C
 * mirrors stay guarded inside {@code PowerManager#send}, because NeoForge still refuses a modded
 * payload on a channel this synthetic connection never negotiated.
 *
 * <p>Every test hands its player back with {@link #release} so the player list does not collect
 * one mock per test.
 *
 * <h2>Coordinates</h2>
 * {@code GameTestHelper#spawn} and {@code #spawnWithNoFreeWill} take <em>structure-relative</em>
 * positions and call {@code absoluteVec} on them internally. Handing them an already-absolute
 * vector transforms it twice and drops the entity millions of blocks from the arena, where no
 * ability can find it - which is exactly what used to make every area-effect test here look like a
 * broken ability. {@code Entity#moveTo} is the opposite: it wants absolute coordinates, so the
 * player position is the one place {@code helper.absoluteVec} is called by hand.
 */
public final class PowersGameTests {

    /** All tests stand inside {@code creator_powers:empty}, a 9x9x9 floor at y = 1. */
    private static final double FLOOR_Y = 2.0D;

    /** Yaw that makes a player look along +X. {@code -sin(yRot)} is the x component of the look. */
    private static final float FACING_EAST = -90.0F;

    // ------------------------------------------------------------------ helpers

    private static Ability ability(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(PowersFeature.NAMESPACE, path);
        return AbilityRegistry.get(id).orElseThrow(
                () -> new IllegalStateException("ability " + id + " is not registered"));
    }

    /**
     * A live {@code ServerPlayer} with a working connection, standing at a structure-relative
     * position. See the class javadoc for why the connection matters.
     */
    private static ServerPlayer testPlayer(GameTestHelper helper, double x, double z, float yRot) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(x, FLOOR_Y, z));
        player.moveTo(pos.x, pos.y, pos.z, yRot, 0.0F);
        player.setYHeadRot(yRot);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.setOnGround(true);
        PowerManager.data().set(player, PlayerPowers.EMPTY);
        return player;
    }

    /** Drops everything the test left on the player and takes the mock back out of the server. */
    private static void release(GameTestHelper helper, ServerPlayer player) {
        ActiveEffects.clearFor(player);
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /**
     * A zombie at a structure-relative position. The coordinates stay relative: the helper does the
     * {@code absoluteVec} itself.
     *
     * <p><b>Every test that calls this must call {@code helper.setNight()} first.</b>
     * {@code creator_powers:empty} has no roof, the GameTest world starts at day, and
     * {@code Zombie#aiStep} runs its {@code isSunBurnTick()} check even on a {@code NoAI} mob -
     * {@code LivingEntity#tick} calls {@code aiStep()} unconditionally, only {@code serverAiStep}
     * is gated by {@code isNoAi()}. A sun-burnt zombie sets {@code isOnFire()} and bleeds health
     * on its own, which turns any fire or health assertion here into a coin flip. Same convention
     * as {@code ArsenalGameTests}.
     */
    private static Zombie zombieAt(GameTestHelper helper, double x, double z) {
        return helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(x, FLOOR_Y, z));
    }

    // ------------------------------------------------------------------ tests

    /** Smoke test: the feature survived the config filter and is live in this session. */
    public static void featureIsEnabled(GameTestHelper helper) {
        helper.assertTrue(CreatorMods.isEnabled(PowersFeature.ID),
                "feature '" + PowersFeature.ID + "' should be enabled in the test session");
        helper.succeed();
    }

    /** {@code /power give} then {@code /power clear}, through the same server API the command uses. */
    public static void grantingFillsSlotsInOrderAndClearingEmptiesThem(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper, 4.5D, 4.5D, FACING_EAST);

        helper.assertTrue(PowerManager.grant(player, ability("dash")), "dash should be granted");
        helper.assertTrue(PowerManager.grant(player, ability("shield_dome")), "dome should be granted");
        helper.assertFalse(PowerManager.grant(player, ability("dash")), "a second dash is a no-op");

        PlayerPowers powers = PowerManager.powersOf(player);
        helper.assertValueEqual(powers.granted(),
                List.of(ability("dash").id(), ability("shield_dome").id()),
                "granted slots");
        helper.assertTrue(powers.slotOf(ability("shield_dome").id()) == 1, "dome should sit in slot 2");

        helper.assertTrue(PowerManager.clear(player) == 2, "clear should report both slots");
        helper.assertTrue(PowerManager.powersOf(player).granted().isEmpty(), "clear should empty the HUD");
        release(helper, player);
        helper.succeed();
    }

    /**
     * The whole keybind decision path: not granted, granted, on cooldown, and ready again after a
     * {@code /power cooldown reset}.
     */
    public static void useStartsACooldownAndIsRefusedUntilItElapses(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper, 4.5D, 4.5D, FACING_EAST);
        Ability dash = ability("dash");
        ResourceLocation dashId = dash.id();

        helper.assertTrue(PowerManager.handleUse(player, dashId) == UseResult.NOT_GRANTED,
                "an ability nobody granted must never fire");
        helper.assertTrue(PowerManager.handleUse(player,
                        ResourceLocation.fromNamespaceAndPath(PowersFeature.NAMESPACE, "not_an_ability"))
                        == UseResult.UNKNOWN,
                "an unknown id must be rejected, not crash the packet handler");

        PowerManager.grant(player, dash);
        long start = helper.getLevel().getGameTime();
        helper.assertTrue(PowerManager.handleUse(player, dashId) == UseResult.ACTIVATED, "dash should fire");

        PlayerPowers afterUse = PowerManager.powersOf(player);
        helper.assertTrue(afterUse.remaining(dashId, start) == dash.cooldownTicks(),
                "the full 60-tick cooldown should start on the tick the ability fired");
        long readyAt = afterUse.readyTick(dashId, start);

        helper.assertTrue(PowerManager.handleUse(player, dashId) == UseResult.ON_COOLDOWN,
                "a second press inside the cooldown must be refused");
        helper.assertTrue(PowerManager.powersOf(player).readyTick(dashId, start) == readyAt,
                "a refused press must not extend the cooldown it was refused by");

        helper.runAfterDelay(20L, () -> {
            long now = helper.getLevel().getGameTime();
            int remaining = PowerManager.powersOf(player).remaining(dashId, now);
            helper.assertTrue(remaining > 0 && remaining <= dash.cooldownTicks() - 20,
                    "the cooldown should have burnt down by ~20 ticks, was " + remaining);

            PowerManager.resetCooldowns(player, dash);
            helper.assertTrue(PowerManager.powersOf(player).isReady(dashId, now),
                    "/power cooldown reset should make it usable immediately");
            release(helper, player);
            helper.succeed();
        });
    }

    /** Dash: real velocity along the look vector, plus the melee-only i-frame window. */
    public static void dashLaunchesThePlayerAndOpensAnIFrameWindow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = testPlayer(helper, 2.5D, 4.5D, FACING_EAST);
        player.setOnGround(true);

        helper.assertTrue(PowerManager.forceUse(player, ability("dash")) == UseResult.ACTIVATED,
                "dash should fire from /power use");

        Vec3 motion = player.getDeltaMovement();
        helper.assertTrue(motion.x > 1.0D,
                "dash should throw the player along +X, delta was " + motion);
        helper.assertTrue(Math.abs(motion.z) < 0.2D,
                "a non-sneaking dash is flattened onto the look direction, delta was " + motion);

        long now = level.getGameTime();
        UUID id = player.getUUID();
        helper.assertTrue(ActiveEffects.dashInvulnerable(id, now), "the i-frame window should be open");
        helper.assertTrue(ActiveEffects.shouldCancelDamage(player, level.damageSources().generic(), now),
                "a hit during the dash window should be swallowed");
        helper.assertFalse(ActiveEffects.shouldCancelDamage(player, level.damageSources().fall(), now),
                "fall damage must still land - the dash is not a free descent");
        helper.assertFalse(ActiveEffects.dashInvulnerable(id, now + 8L),
                "the window is 8 ticks, not permanent");
        release(helper, player);
        helper.succeed();
    }

    /** Fire Burst: 7 m / 70 degree cone, so what is behind the player is untouched. */
    public static void fireBurstBurnsOnlyWhatIsInTheCone(GameTestHelper helper) {
        helper.setNight();
        ServerPlayer player = testPlayer(helper, 4.5D, 4.5D, FACING_EAST);
        Zombie inCone = zombieAt(helper, 7.5D, 4.5D);
        Zombie behind = zombieAt(helper, 1.5D, 4.5D);

        helper.assertTrue(PowerManager.forceUse(player, ability("fire_burst")) == UseResult.ACTIVATED,
                "fire burst should fire");

        helper.runAfterDelay(2L, () -> {
            helper.assertTrue(inCone.isOnFire(), "the zombie in front should be alight");
            helper.assertTrue(inCone.getHealth() < inCone.getMaxHealth(),
                    "the zombie in front should have taken the 6 damage");
            helper.assertFalse(behind.isOnFire(), "the zombie behind the player must not catch fire");
            helper.assertTrue(behind.getHealth() == behind.getMaxHealth(),
                    "the zombie behind the player must take nothing");
            release(helper, player);
            helper.succeed();
        });
    }

    /** Ender Pull: the raycast finds the target and the target ends up closer. */
    public static void enderPullDragsTheTargetTowardsThePlayer(GameTestHelper helper) {
        helper.setNight();
        ServerPlayer player = testPlayer(helper, 1.5D, 4.5D, FACING_EAST);
        Zombie target = zombieAt(helper, 7.5D, 4.5D);
        double startX = target.getX();

        Ability pull = ability("ender_pull");
        helper.assertTrue(pull.canUse(player), "a zombie straight ahead is a valid pull target");
        helper.assertTrue(PowerManager.forceUse(player, pull) == UseResult.ACTIVATED, "pull should fire");

        helper.assertTrue(target.getDeltaMovement().x < -0.5D,
                "the target should be yanked back along -X, delta was " + target.getDeltaMovement());

        helper.runAfterDelay(12L, () -> {
            helper.assertTrue(target.getX() < startX - 1.0D,
                    "the target should have travelled at least a block towards the player, moved "
                            + (startX - target.getX()));
            release(helper, player);
            helper.succeed();
        });
    }

    /** Ender Pull with nothing under the crosshair refuses before anything is spent. */
    public static void enderPullWithNoTargetIsRefused(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper, 4.5D, 4.5D, FACING_EAST);
        Ability pull = ability("ender_pull");
        PowerManager.grant(player, pull);

        helper.assertFalse(pull.canUse(player), "an empty arena has nothing to pull");
        helper.assertTrue(PowerManager.handleUse(player, pull.id()) == UseResult.CANNOT_USE,
                "the use should be refused");
        helper.assertTrue(PowerManager.powersOf(player).isReady(pull.id(), helper.getLevel().getGameTime()),
                "a refused use must not burn the cooldown");
        release(helper, player);
        helper.succeed();
    }

    /** Mob Freeze: hostiles only, and everything it touched is handed back exactly as it was found. */
    public static void mobFreezeHoldsHostilesAndThawsThem(GameTestHelper helper) {
        helper.setNight();
        ServerPlayer player = testPlayer(helper, 4.5D, 4.5D, FACING_EAST);
        Zombie one = zombieAt(helper, 2.5D, 2.5D);
        Zombie two = zombieAt(helper, 6.5D, 6.5D);
        Cow cow = helper.spawn(EntityType.COW, new Vec3(6.5D, FLOOR_Y, 2.5D));

        helper.assertTrue(PowerManager.forceUse(player, ability("mob_freeze")) == UseResult.ACTIVATED,
                "freeze should fire");

        for (Mob mob : List.of(one, two)) {
            helper.assertTrue(mob.isNoAi(), "a frozen zombie should have no AI");
            helper.assertTrue(mob.isNoGravity(), "a frozen zombie should be pinned in the air it is in");
            helper.assertTrue(mob.getTags().contains(ActiveEffects.FROZEN_TAG),
                    "a frozen mob carries the crash-recovery tag");
            helper.assertTrue(ActiveEffects.isFrozen(mob.getUUID()), "and is tracked by this session");
        }
        helper.assertFalse(cow.isNoAi(), "a cow is not an Enemy and must keep grazing");

        // The freeze runs 100 ticks; the thaw restores the flags the mobs had when it started.
        helper.runAfterDelay(115L, () -> {
            for (Mob mob : List.of(one, two)) {
                helper.assertFalse(mob.isNoAi(), "the zombie should have its AI back");
                helper.assertFalse(mob.isNoGravity(), "the zombie should fall again");
                helper.assertFalse(mob.getTags().contains(ActiveEffects.FROZEN_TAG),
                        "the crash-recovery tag should be gone");
                helper.assertFalse(ActiveEffects.isFrozen(mob.getUUID()), "and the mob untracked");
            }
            release(helper, player);
            helper.succeed();
        });
    }

    /** Ground Pound refuses on the ground, and its shockwave damages and throws what it lands on. */
    public static void groundPoundNeedsAirAndItsShockwaveThrowsMobs(GameTestHelper helper) {
        helper.setNight();
        ServerLevel level = helper.getLevel();
        ServerPlayer player = testPlayer(helper, 4.5D, 4.5D, FACING_EAST);
        Ability pound = ability("ground_pound");

        player.setOnGround(true);
        helper.assertFalse(pound.canUse(player), "a pound from standing would be a no-op on camera");
        player.setOnGround(false);
        helper.assertTrue(pound.canUse(player), "airborne is the whole point of the ability");

        Zombie near = zombieAt(helper, 6.0D, 4.5D);
        Zombie far = zombieAt(helper, 4.5D, 2.0D);
        near.setNoGravity(true);
        far.setNoGravity(true);

        PowerManager.onPoundImpact(player, level, level.getGameTime(), 6.0D);

        for (Mob mob : List.of(near, far)) {
            helper.assertTrue(mob.getHealth() < mob.getMaxHealth(),
                    "the shockwave should have hurt every mob inside the radius");
            helper.assertTrue(mob.getDeltaMovement().horizontalDistance() > 0.2D,
                    "the shockwave should have thrown it outwards, delta was " + mob.getDeltaMovement());
            helper.assertTrue(mob.getDeltaMovement().y > 0.0D, "and lifted it off the floor");
        }
        helper.assertTrue(near.getHealth() < far.getHealth() + 8.0F,
                "damage falls off with distance from the epicentre");
        release(helper, player);
        helper.succeed();
    }

    /** Shield Dome: the buffs go on, arrows are eaten, melee still lands. */
    public static void shieldDomeCoversThePlayerAndVoidsArrows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = testPlayer(helper, 4.5D, 4.5D, FACING_EAST);

        helper.assertTrue(PowerManager.forceUse(player, ability("shield_dome")) == UseResult.ACTIVATED,
                "dome should fire");

        long now = level.getGameTime();
        helper.assertTrue(player.getAbsorptionAmount() >= 8.0F,
                "the dome is worth four golden hearts, was " + player.getAbsorptionAmount());
        helper.assertTrue(player.hasEffect(MobEffects.DAMAGE_RESISTANCE), "Resistance II should be on");
        AttributeInstance armour = player.getAttribute(Attributes.ARMOR);
        helper.assertTrue(armour != null && armour.hasModifier(ActiveEffects.DOME_ARMOR_ID),
                "the transient armour modifier should be applied");
        helper.assertTrue(ActiveEffects.domeActive(player.getUUID(), now), "the dome should be live");

        helper.assertTrue(ActiveEffects.shouldCancelDamage(player,
                        level.damageSources().explosion(null, null), now),
                "a creeper should do nothing to a domed player");
        helper.assertFalse(ActiveEffects.shouldCancelDamage(player,
                        level.damageSources().generic(), now),
                "contact damage still lands - the dome is cover, not invulnerability");

        Arrow arrow = helper.spawn(EntityType.ARROW, new Vec3(7.5D, 3.0D, 4.5D));
        arrow.setDeltaMovement(new Vec3(-0.6D, 0.0D, 0.0D));

        helper.runAfterDelay(3L, () -> {
            helper.assertTrue(arrow.isRemoved(), "an arrow flying into the dome should be voided");
            release(helper, player);
            helper.succeed();
        });
    }

    /** The dome is a 10-second window: everything it granted comes back off when it expires. */
    public static void shieldDomeExpiresAndHandsBackTheBuffs(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper, 4.5D, 4.5D, FACING_EAST);
        PowerManager.forceUse(player, ability("shield_dome"));

        helper.assertTrue(ActiveEffects.domeRemaining(player.getUUID(),
                helper.getLevel().getGameTime()) > 190, "the dome should start with its full 200 ticks");

        helper.runAfterDelay(215L, () -> {
            long now = helper.getLevel().getGameTime();
            helper.assertFalse(ActiveEffects.domeActive(player.getUUID(), now), "the dome should be over");
            AttributeInstance armour = player.getAttribute(Attributes.ARMOR);
            helper.assertTrue(armour != null && !armour.hasModifier(ActiveEffects.DOME_ARMOR_ID),
                    "the armour modifier must be taken back off, or it stacks every cast");
            AttributeInstance toughness = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
            helper.assertTrue(toughness != null && !toughness.hasModifier(ActiveEffects.DOME_TOUGHNESS_ID),
                    "and so must the toughness modifier");
            helper.assertTrue(player.getAbsorptionAmount() == 0.0F,
                    "the absorption the dome gave should be gone, was " + player.getAbsorptionAmount());
            helper.assertFalse(ActiveEffects.shouldCancelDamage(player,
                            helper.getLevel().damageSources().explosion(null, null), now),
                    "and explosions should hurt again");
            release(helper, player);
            helper.succeed();
        });
    }

    private PowersGameTests() {
    }
}
