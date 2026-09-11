package dev.riftal.creator.features.arsenal.gametest;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.arsenal.ArsenalFeature;
import dev.riftal.creator.features.arsenal.command.Weapon;
import dev.riftal.creator.features.arsenal.entity.GrappleHookEntity;
import dev.riftal.creator.features.arsenal.entity.StormArrowEntity;
import dev.riftal.creator.features.arsenal.item.GrappleBladeItem;
import dev.riftal.creator.features.arsenal.item.GravityHammerItem;
import dev.riftal.creator.features.arsenal.item.SoulScytheItem;
import dev.riftal.creator.features.arsenal.mechanic.DamageMath;
import dev.riftal.creator.features.arsenal.mechanic.GrappleManager;
import dev.riftal.creator.features.arsenal.mechanic.GravitySlam;
import dev.riftal.creator.features.arsenal.mechanic.Impact;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * GameTest bodies for the {@code arsenal} feature. Vanilla API only, so both loaders can call them.
 *
 * <p>Add a {@code public static void name(GameTestHelper helper)} here, then one annotated stub in
 * {@code fabric/src/gametest/java/.../ArsenalFabricGameTests.java} and one in
 * {@code neoforge/src/main/java/.../ArsenalNeoForgeGameTests.java}.
 *
 * <p>Everything the four weapons do needs a real {@code ServerPlayer} - the hammer, the scythe's
 * melee hooks and the grapple all take one - so these use
 * {@link GameTestHelper#makeMockServerPlayerInLevel()} rather than {@code makeMockPlayer}, and
 * every one of them hands the player back to {@link #release} so it does not linger in the server's
 * player list and leak into a later test in the same batch.
 *
 * <p><b>Why every combat test carries its own {@code batch} on both stubs.</b> Tests that share a
 * batch run <em>simultaneously</em> in arenas the grid spawner packs 14 blocks apart
 * ({@code StructureGridSpawner.SPACE_BETWEEN_COLUMNS} = 5 either side of a 9x9x9 {@code empty}),
 * and they share one {@link net.minecraft.server.level.ServerLevel}. Every area-of-effect helper in
 * this mod selects by world distance, not by arena: a neighbour's sweep with a radius wider than
 * that pitch reaches straight into ours and sets our zombies alight or hurts them. That is not
 * hypothetical - {@code stormArrowUnchargedNeverStrikes} failed on NeoForge for exactly this
 * reason, its zombie lit on fire by a 32-block burn sweep in a batch-mate from another feature 22
 * blocks away, while the same test passed on Fabric only because that loader happened to lay the
 * two arenas farther apart. Arena layout is not a guarantee we may lean on, so any test here that
 * asserts on health, fire, mob effects, position or motion runs alone in a batch of one; the four
 * that only read registries and item stacks are immune and stay in {@code defaultBatch}. Arsenal's
 * own reach tops out at {@code DamageMath.SLAM_RADIUS} (6), comfortably inside the pitch, so this
 * is about keeping foreign effects out rather than containing ours.
 */
public final class ArsenalGameTests {

    private static final double EPSILON = 1.0E-3D;

    /** Smoke test: the feature survived the config filter and is live in this session. */
    public static void featureIsEnabled(GameTestHelper helper) {
        helper.assertTrue(CreatorMods.isEnabled(ArsenalFeature.ID),
                "feature '" + ArsenalFeature.ID + "' should be enabled in the test session");
        helper.succeed();
    }

    /**
     * The four weapons exist, carry their rarity, and the scythe really does ship the two extra
     * attribute modifiers - which is the half of {@code SoulScytheItem.attributes()} that plain
     * JUnit cannot reach, because building one touches the attribute registry.
     */
    public static void weaponsCarryTheirRarityAndAttributes(GameTestHelper helper) {
        ItemStack blade = new ItemStack(ArsenalFeature.GRAPPLE_BLADE.get());
        ItemStack bow = new ItemStack(ArsenalFeature.STORM_BOW.get());
        ItemStack hammer = new ItemStack(ArsenalFeature.GRAVITY_HAMMER.get());
        ItemStack scythe = new ItemStack(ArsenalFeature.SOUL_SCYTHE.get());

        helper.assertValueEqual(blade.getRarity(), Rarity.RARE, "grapple blade rarity");
        helper.assertValueEqual(bow.getRarity(), Rarity.EPIC, "storm bow rarity");
        helper.assertValueEqual(hammer.getRarity(), Rarity.EPIC, "gravity hammer rarity");
        helper.assertValueEqual(scythe.getRarity(), Rarity.EPIC, "soul scythe rarity");

        helper.assertValueEqual(bow.getMaxDamage(), 600, "storm bow durability");

        // 3 (the createAttributes argument) + the tier bonus; the player's own 1.0 is added on top
        // in game, giving the 7 / 12 / 9 the plan promises.
        assertModifier(helper, blade, Item.BASE_ATTACK_DAMAGE_ID, 6.0D, "grapple blade attack damage");
        assertModifier(helper, blade, Item.BASE_ATTACK_SPEED_ID, -2.4D, "grapple blade attack speed");
        assertModifier(helper, hammer, Item.BASE_ATTACK_DAMAGE_ID, 11.0D, "gravity hammer attack damage");
        assertModifier(helper, hammer, Item.BASE_ATTACK_SPEED_ID, -3.2D, "gravity hammer attack speed");
        assertModifier(helper, scythe, Item.BASE_ATTACK_DAMAGE_ID, 8.0D, "soul scythe attack damage");
        assertModifier(helper, scythe, Item.BASE_ATTACK_SPEED_ID, -2.8D, "soul scythe attack speed");
        assertModifier(helper, scythe, ArsenalFeature.res("scythe_sweep"),
                SoulScytheItem.SWEEP_MODIFIER, "soul scythe sweeping ratio");
        assertModifier(helper, scythe, ArsenalFeature.res("scythe_reach"),
                SoulScytheItem.REACH_MODIFIER, "soul scythe reach");

        // The two extras must be main-hand only and hang off the right attributes.
        ItemAttributeModifiers modifiers = scythe.get(DataComponents.ATTRIBUTE_MODIFIERS);
        helper.assertTrue(modifiers != null, "the scythe should carry an attribute modifier component");
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            helper.assertValueEqual(entry.slot(), EquipmentSlotGroup.MAINHAND,
                    "slot group of " + entry.modifier().id());
        }
        helper.assertTrue(hasEntry(modifiers, Attributes.SWEEPING_DAMAGE_RATIO,
                        ArsenalFeature.res("scythe_sweep")),
                "creator_arsenal:scythe_sweep should modify sweeping_damage_ratio");
        helper.assertTrue(hasEntry(modifiers, Attributes.ENTITY_INTERACTION_RANGE,
                        ArsenalFeature.res("scythe_reach")),
                "creator_arsenal:scythe_reach should modify entity_interaction_range");

        helper.succeed();
    }

    /** {@code /arsenal give @s all} puts all four weapons plus a stack of arrows in the inventory. */
    public static void giveCommandHandsOutAllFourAndArrows(GameTestHelper helper) {
        ServerPlayer player = spawnPlayer(helper, new Vec3(2.5D, 1.0D, 2.5D), 0.0F);
        try {
            run(helper, player, "arsenal give @s all");

            for (Weapon weapon : Weapon.values()) {
                helper.assertTrue(player.getInventory().contains(new ItemStack(weapon.item())),
                        "inventory should contain " + weapon.id() + " after /arsenal give all");
            }
            helper.assertTrue(player.getInventory().contains(new ItemStack(Items.ARROW)),
                    "the storm bow should come with arrows");
        } finally {
            release(helper, player);
        }
        helper.succeed();
    }

    /** An unknown weapon name is an error, not a fallback weapon nobody asked for. */
    public static void giveCommandRejectsAnUnknownWeapon(GameTestHelper helper) {
        ServerPlayer player = spawnPlayer(helper, new Vec3(2.5D, 1.0D, 2.5D), 0.0F);
        try {
            run(helper, player, "arsenal give @s banana");

            for (Weapon weapon : Weapon.values()) {
                helper.assertFalse(player.getInventory().contains(new ItemStack(weapon.item())),
                        "nothing should have been given, but " + weapon.id() + " arrived");
            }
            helper.assertFalse(player.getInventory().contains(new ItemStack(Items.ARROW)),
                    "no arrows either");
        } finally {
            release(helper, player);
        }
        helper.succeed();
    }

    /**
     * A charged storm arrow calls its bolt, blasts what is underneath it, and - the point of the
     * whole visual-only trick - sets nothing on fire. A creator's build must survive the shot.
     */
    public static void stormArrowChargedStrikesWithoutFire(GameTestHelper helper) {
        helper.setNight();
        BlockPos impact = new BlockPos(4, 1, 4);
        Zombie victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, impact);
        float fullHealth = victim.getHealth();

        StormArrowEntity arrow = new StormArrowEntity(ArsenalFeature.STORM_ARROW.get(), helper.getLevel());
        Vec3 from = helper.absoluteVec(new Vec3(4.5D, 5.0D, 4.5D));
        arrow.setPos(from.x, from.y, from.z);
        arrow.setCharged(true);
        arrow.setDeltaMovement(0.0D, -1.0D, 0.0D);
        helper.getLevel().addFreshEntity(arrow);

        boolean[] sawBolt = {false};
        helper.onEachTick(() -> {
            if (!boltsAround(helper, impact).isEmpty()) {
                sawBolt[0] = true;
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(victim.getHealth() < fullHealth - 5.0F,
                    "a charged strike should take far more than an arrow's worth of health, but "
                            + victim.getHealth() + " of " + fullHealth + " is left");
            helper.assertTrue(sawBolt[0], "a lightning bolt should have been spawned at the impact");
            helper.assertFalse(victim.isOnFire(), "the visual-only bolt must not set the victim alight");
            helper.assertTrue(arrow.isRemoved(), "a struck arrow discards itself, it is not pickable");
            helper.forEveryBlockInStructure(pos -> helper.assertBlockNotPresent(Blocks.FIRE, pos));
        });
    }

    /** A half-drawn shot is an ordinary arrow: it hurts a little and calls nothing. */
    public static void stormArrowUnchargedNeverStrikes(GameTestHelper helper) {
        helper.setNight();
        BlockPos impact = new BlockPos(4, 1, 4);
        Zombie victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, impact);
        float fullHealth = victim.getHealth();

        StormArrowEntity arrow = new StormArrowEntity(ArsenalFeature.STORM_ARROW.get(), helper.getLevel());
        Vec3 from = helper.absoluteVec(new Vec3(4.5D, 5.0D, 4.5D));
        arrow.setPos(from.x, from.y, from.z);
        arrow.setCharged(false);
        arrow.setDeltaMovement(0.0D, -1.0D, 0.0D);
        helper.getLevel().addFreshEntity(arrow);

        helper.onEachTick(() -> helper.assertTrue(boltsAround(helper, impact).isEmpty(),
                "an uncharged arrow must never call a bolt"));

        // The arrow lands within a couple of ticks; the wait is there to prove nothing turns up
        // late, so it is a delay and not succeedOnTickWhen - that one demands the criterion first
        // pass on exactly its tick, which for an assertion that is already true at tick 2 is an
        // instant "Succeeded in invalid tick".
        helper.runAfterDelay(30L, () -> {
            helper.assertTrue(victim.getHealth() < fullHealth,
                    "the arrow should still have hit the zombie");
            helper.assertTrue(victim.getHealth() > fullHealth - DamageMath.LIGHTNING_CENTRE_DAMAGE,
                    "an uncharged arrow must not deal blast damage");
            helper.assertFalse(victim.isOnFire(), "nothing about a plain arrow burns");
            helper.succeed();
        });
    }

    /** Right-click lifts everything living within six blocks and starts the eight second wipe. */
    public static void hammerLiftsEverythingInRange(GameTestHelper helper) {
        helper.setNight();
        ServerPlayer player = spawnPlayer(helper, new Vec3(4.5D, 1.0D, 4.5D), 0.0F);
        List<Zombie> victims = new ArrayList<>();
        victims.add(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 4)));
        victims.add(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(6, 1, 4)));
        victims.add(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 1, 6)));

        GravityHammerItem hammer = (GravityHammerItem) ArsenalFeature.GRAVITY_HAMMER.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(hammer));
        hammer.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        helper.assertTrue(player.getCooldowns().isOnCooldown(hammer),
                "the hammer should be on its 8 second cooldown straight after the swing");
        for (Zombie victim : victims) {
            helper.assertLivingEntityHasMobEffect(victim, MobEffects.LEVITATION, 1);
        }

        // A second swing while the wipe is still running does nothing at all.
        InteractionResult second = hammer.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult();
        helper.assertValueEqual(second, InteractionResult.PASS,
                "a hammer on cooldown should pass the right-click through");

        // ... and /arsenal slam cancel frees anything still hanging in the air.
        GravitySlam.cancel(player);
        for (Zombie victim : victims) {
            helper.assertFalse(victim.hasEffect(MobEffects.LEVITATION),
                    "/arsenal slam cancel (GravitySlam.cancel) must drop the levitation again");
        }

        release(helper, player);
        helper.succeed();
    }

    /** Once the hang time is up the levitation is stripped and everything is thrown down. */
    public static void hammerSlamsAfterTheHangTime(GameTestHelper helper) {
        helper.setNight();
        ServerPlayer player = spawnPlayer(helper, new Vec3(4.5D, 1.0D, 4.5D), 0.0F);
        Zombie victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 4));
        float fullHealth = victim.getHealth();

        GravityHammerItem hammer = (GravityHammerItem) ArsenalFeature.GRAVITY_HAMMER.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(hammer));
        hammer.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        helper.runAfterDelay(5L, () -> helper.assertLivingEntityHasMobEffect(
                victim, MobEffects.LEVITATION, 1));

        helper.runAfterDelay(GravitySlam.LIFT_TICKS + 3L, () -> {
            helper.assertFalse(victim.hasEffect(MobEffects.LEVITATION),
                    "the slam should have stripped the levitation at +" + GravitySlam.LIFT_TICKS + " ticks");
            // Three ticks after the slam the zombie is either still being driven into the ground or
            // has already landed and taken its impact damage. Both are the slam; neither is "nothing
            // happened", which is what this guards against.
            boolean thrownDown = victim.getDeltaMovement().y < -0.5D;
            boolean alreadyHurt = victim.getHealth() < fullHealth;
            helper.assertTrue(thrownDown || alreadyHurt,
                    "the slam should have thrown the zombie down or already hurt it, but dy="
                            + victim.getDeltaMovement().y + " and health=" + victim.getHealth());
            release(helper, player);
            helper.succeed();
        });
    }

    /** Every hit heals a quarter of what it dealt and passes half of it to the neighbour. */
    public static void scytheHealsAndSweeps(GameTestHelper helper) {
        helper.setNight();
        ServerPlayer player = spawnPlayer(helper, new Vec3(2.5D, 1.0D, 4.5D), 0.0F);
        try {
            player.setHealth(10.0F);
            Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 4));
            Zombie neighbour = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 1, 4));
            float targetBefore = target.getHealth();
            float neighbourBefore = neighbour.getHealth();

            SoulScytheItem scythe = (SoulScytheItem) ArsenalFeature.SOUL_SCYTHE.get();
            ItemStack stack = new ItemStack(scythe);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);

            // Exactly what Player#attack does: the damage-bonus hook, then the hurt, then the
            // post-hit hook. Driving it by hand keeps the test off the attack-cooldown timer.
            DamageSource source = helper.getLevel().damageSources().playerAttack(player);
            scythe.getAttackDamageBonus(target, 8.0F, source);
            target.hurt(source, 8.0F);
            float dealt = targetBefore - target.getHealth();
            scythe.postHurtEnemy(stack, target, player);

            helper.assertTrue(dealt > 0.0F, "the scythe should have hurt its target");
            float expected = 10.0F + DamageMath.lifesteal(dealt);
            helper.assertTrue(Math.abs(player.getHealth() - expected) < EPSILON,
                    "lifesteal should have healed to " + expected + " but health is " + player.getHealth());

            float sweptOff = neighbourBefore - neighbour.getHealth();
            helper.assertTrue(sweptOff > 0.0F, "the always-on sweep should have hit the neighbour");
            helper.assertTrue(sweptOff < dealt,
                    "a swept neighbour takes half the damage, not all of it (" + sweptOff + " vs " + dealt + ")");
        } finally {
            release(helper, player);
        }
        helper.succeed();
    }

    /** A kill releases a wisp; twenty ticks later it lands as two golden hearts. */
    public static void scytheKillGrantsAbsorptionAfterTheWisp(GameTestHelper helper) {
        helper.setNight();
        ServerPlayer player = spawnPlayer(helper, new Vec3(2.5D, 1.0D, 4.5D), 0.0F);
        player.setAbsorptionAmount(0.0F);
        Zombie victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 4));
        victim.setHealth(1.0F);

        SoulScytheItem scythe = (SoulScytheItem) ArsenalFeature.SOUL_SCYTHE.get();
        ItemStack stack = new ItemStack(scythe);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        DamageSource source = helper.getLevel().damageSources().playerAttack(player);
        scythe.getAttackDamageBonus(victim, 8.0F, source);
        victim.hurt(source, 8.0F);
        scythe.postHurtEnemy(stack, victim, player);

        helper.assertTrue(victim.isDeadOrDying(), "the zombie should be dead");
        helper.assertTrue(player.getAbsorptionAmount() < DamageMath.ABSORPTION_PER_SOUL,
                "the soul has to fly to the player first - absorption must not land on the kill tick");

        helper.runAfterDelay(30L, () -> {
            helper.assertTrue(Math.abs(player.getAbsorptionAmount() - DamageMath.ABSORPTION_PER_SOUL) < EPSILON,
                    "the wisp should have granted " + DamageMath.ABSORPTION_PER_SOUL
                            + " absorption but the player has " + player.getAbsorptionAmount());
            release(helper, player);
            helper.succeed();
        });
    }

    /** Right-click fires a hook, it bites the wall, and the player is reeled across the arena. */
    public static void grappleHookBitesAndReelsThePlayer(GameTestHelper helper) {
        for (int y = 1; y <= 4; y++) {
            for (int z = 3; z <= 6; z++) {
                helper.setBlock(new BlockPos(7, y, z), Blocks.STONE);
            }
        }

        Vec3 start = new Vec3(1.5D, 1.0D, 4.5D);
        ServerPlayer player = spawnPlayer(helper, start, yawTowards(start, new Vec3(7.0D, 2.5D, 4.5D)));
        GrappleBladeItem blade = (GrappleBladeItem) ArsenalFeature.GRAPPLE_BLADE.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(blade));
        blade.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        helper.assertTrue(GrappleManager.hasHook(player), "firing the blade should put a hook in the world");
        helper.assertEntityPresent(ArsenalFeature.GRAPPLE_HOOK.get());
        helper.assertTrue(player.getCooldowns().isOnCooldown(blade),
                "firing should start the 20 tick cooldown");

        boolean[] bit = {false};
        helper.onEachTick(() -> {
            GrappleHookEntity hook = GrappleManager.hookOf(player);
            if (hook != null && hook.getState() == GrappleHookEntity.STATE_ATTACHED) {
                bit[0] = true;
            }
        });

        // The reel is asserted on the velocity the server hands the player, not on the distance the
        // player covers, and that is a property of the harness rather than a softer test. A mock
        // GameTest player has no client and no ticking connection: ServerPlayer#doTick - the only
        // thing that runs a player's physics, via the connection's packet tick - is never called
        // for it, so nothing ever integrates setDeltaMovement and position() stays exactly where it
        // was put. GrapplePull's whole job is choosing that velocity, so this checks the real
        // output: aimed at the wall, lifted, and inside the speed cap.
        helper.succeedWhen(() -> {
            helper.assertTrue(bit[0], "the hook should have bitten the wall");
            helper.assertTrue(GrappleManager.isPulling(player),
                    "the pull task should be alive - it also runs the safe-landing window");

            Vec3 velocity = player.getDeltaMovement();
            helper.assertTrue(velocity.x > 0.3D,
                    "the reel should be driving the player at the wall (+X), but the velocity is "
                            + velocity);
            helper.assertTrue(Math.abs(velocity.z) < 0.1D,
                    "the anchor is straight ahead, so the reel should not push sideways: " + velocity);
            helper.assertTrue(velocity.y >= DamageMath.GRAPPLE_LIFT - EPSILON,
                    "every pull tick adds the upward nudge that clears ledges, but dy=" + velocity.y);
            helper.assertTrue(velocity.length() <= DamageMath.GRAPPLE_MAX_SPEED + DamageMath.GRAPPLE_LIFT + EPSILON,
                    "the pull must never exceed the speed cap, but it is " + velocity.length());
            helper.assertTrue(player.fallDistance == 0.0F,
                    "fall distance is zeroed for the whole flight so the landing is safe");
            release(helper, player);
        });
    }

    /** Right-clicking again cuts the line - even while the blade is still on cooldown. */
    public static void grappleSecondUseCutsTheLine(GameTestHelper helper) {
        Vec3 start = new Vec3(4.5D, 1.0D, 4.5D);
        ServerPlayer player = spawnPlayer(helper, start, 0.0F);
        GrappleBladeItem blade = (GrappleBladeItem) ArsenalFeature.GRAPPLE_BLADE.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(blade));

        blade.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        GrappleHookEntity hook = GrappleManager.hookOf(player);
        helper.assertTrue(hook != null, "the first use should have fired a hook");

        blade.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(hook.getState(), GrappleHookEntity.STATE_RETURNING,
                "the second use should have cut the line");

        helper.succeedWhen(() -> {
            helper.assertTrue(hook.isRemoved(), "the cut hook should fly home and despawn");
            helper.assertFalse(GrappleManager.hasHook(player), "and the player should be hook-free");
            release(helper, player);
        });
    }

    /**
     * The hammer's touchdown damage is not swallowed by the fall damage the slam itself caused.
     *
     * <p>This is the seam, tested exactly rather than end to end, because the end-to-end number
     * depends on how far Levitation II happened to lift the mob. {@code GravitySlam.tick()} hurts a
     * victim in the same server tick it landed, and {@code Entity#move -> checkFallDamage} has
     * already run by then and left {@code invulnerableTime = 20, lastHurt = fallDamage}, so a plain
     * {@code hurt} used to apply only {@code slam - fall}: 6.0 of hammer on 4 points of fall damage
     * landed as 2, and the plan's "everything dead or crawling" beat was eight healthy zombies
     * bouncing once.
     *
     * <p>The control victim takes the same blow with no fall damage in front of it. Both numbers go
     * through the same armour reduction, so comparing them needs no vanilla damage arithmetic here.
     */
    public static void slamDamageSurvivesTheFallDamageWindow(GameTestHelper helper) {
        ServerPlayer player = spawnPlayer(helper, new Vec3(4.5D, 1.0D, 4.5D), 0.0F);
        try {
            DamageSource source = helper.getLevel().damageSources().playerAttack(player);
            Zombie landed = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
            Zombie control = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(6, 1, 6));

            // Fall damage first, exactly as a real touchdown delivers it.
            landed.hurt(source, 4.0F);
            float afterFall = landed.getHealth();
            helper.assertTrue(landed.invulnerableTime > 10,
                    "the fall should have opened an invulnerability window, or this test proves nothing");

            Impact.hurtThroughCooldown(landed, source, DamageMath.SLAM_CENTRE_DAMAGE);
            float slamLoss = afterFall - landed.getHealth();

            float controlBefore = control.getHealth();
            Impact.hurtThroughCooldown(control, source, DamageMath.SLAM_CENTRE_DAMAGE);
            float soloLoss = controlBefore - control.getHealth();

            helper.assertTrue(soloLoss > 0.0F, "the control victim should have taken the slam");
            helper.assertTrue(Math.abs(slamLoss - soloLoss) < EPSILON,
                    "the slam must land in full on top of fall damage: it took " + slamLoss
                            + " where an undamaged victim took " + soloLoss);
        } finally {
            release(helper, player);
        }
        helper.succeed();
    }

    /**
     * The blast hits the mob the arrow physically struck as hard as it hits that mob's neighbours.
     *
     * <p>{@code onHitEntity} runs {@code super} first - which deals the arrow's own damage and opens
     * the victim's invulnerability window - and then calls the blast, so the target you aimed at
     * used to take {@code 10 - arrowDamage} while everything standing next to it took the full 10.
     * The arrow is given a large base damage here precisely so that swallowing it is measurable:
     * with the old code the total could never exceed {@code max(arrowDamage, 10)}.
     */
    public static void stormArrowBlastFullyHitsWhatItStruck(GameTestHelper helper) {
        helper.setNight();
        Zombie direct = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 1, 4));
        Zombie bystander = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(6, 1, 4));
        float directFull = direct.getHealth();
        float bystanderFull = bystander.getHealth();

        StormArrowEntity arrow = new StormArrowEntity(ArsenalFeature.STORM_ARROW.get(), helper.getLevel());
        Vec3 from = helper.absoluteVec(new Vec3(4.5D, 5.0D, 4.5D));
        arrow.setPos(from.x, from.y, from.z);
        arrow.setBaseDamage(6.0D);
        arrow.setCharged(true);
        arrow.setDeltaMovement(0.0D, -1.0D, 0.0D);
        helper.getLevel().addFreshEntity(arrow);

        helper.succeedWhen(() -> {
            helper.assertTrue(arrow.isRemoved(), "the arrow should have struck and discarded itself");
            float directLoss = directFull - direct.getHealth();
            float bystanderLoss = bystanderFull - bystander.getHealth();
            helper.assertTrue(bystanderLoss > 0.0F, "the blast should have reached the bystander");
            helper.assertTrue(directLoss > DamageMath.LIGHTNING_CENTRE_DAMAGE + 2.0F,
                    "the struck mob should take the arrow AND the whole blast, but it lost only "
                            + directLoss + " (the bystander lost " + bystanderLoss + ")");
        });
    }

    /**
     * When vanilla's own sweep already fired for this swing, ours stays out of the way - but the
     * souls of whatever that sweep killed are still collected.
     *
     * <p>Vanilla runs its sweep inline in {@code Player#attack}, between
     * {@code Item#getAttackDamageBonus} and {@code Item#postHurtEnemy}, over this same box and with
     * this weapon's own sweeping ratio - so its neighbour damage is roughly 7.75 against our
     * {@code dealt x 0.5}. Ours then hit the invulnerability window and did nothing at all, while
     * still firing a second knockback, particle and sound. The knockback is what this test watches,
     * because "did no damage" is true both before and after the fix.
     *
     * <p>The vanilla sweep is simulated rather than provoked: reaching its real condition needs an
     * attack-strength-charged, grounded, non-sprinting swing that a mock player cannot produce.
     */
    public static void scytheDoesNotSweepOnTopOfVanillasSweep(GameTestHelper helper) {
        helper.setNight();
        ServerPlayer player = spawnPlayer(helper, new Vec3(2.5D, 1.0D, 4.5D), 0.0F);
        player.setAbsorptionAmount(0.0F);
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 4));
        Zombie survivor = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 1, 4));
        Zombie doomed = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 5));
        doomed.setHealth(1.0F);

        SoulScytheItem scythe = (SoulScytheItem) ArsenalFeature.SOUL_SCYTHE.get();
        ItemStack stack = new ItemStack(scythe);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        DamageSource source = helper.getLevel().damageSources().playerAttack(player);
        scythe.getAttackDamageBonus(target, 8.0F, source);
        target.hurt(source, 8.0F);

        // Vanilla's sweep, as it would have run right here.
        survivor.hurt(source, 7.75F);
        doomed.hurt(source, 7.75F);
        survivor.setDeltaMovement(Vec3.ZERO);
        float survivorHealth = survivor.getHealth();
        helper.assertTrue(doomed.isDeadOrDying(), "the simulated vanilla sweep should have killed the 1 HP zombie");

        scythe.postHurtEnemy(stack, target, player);

        helper.assertTrue(survivor.getHealth() == survivorHealth,
                "our sweep must not run on a swing vanilla already swept");
        helper.assertTrue(survivor.getDeltaMovement().equals(Vec3.ZERO),
                "not even the knockback: the neighbour was pushed a second time, delta is "
                        + survivor.getDeltaMovement());

        // ... and the kill vanilla's sweep made still pays out a soul.
        helper.runAfterDelay(30L, () -> {
            helper.assertTrue(player.getAbsorptionAmount() >= DamageMath.ABSORPTION_PER_SOUL - EPSILON,
                    "a neighbour killed by vanilla's sweep should still release a soul, but absorption is "
                            + player.getAbsorptionAmount());
            release(helper, player);
            helper.succeed();
        });
    }

    /**
     * The reel really does carry the player across the gap - the plan's {@code player.getX() > 10}.
     *
     * <p>A mock GameTest player has no client and no ticking connection, so
     * {@code ServerPlayer#doTick} never runs and nothing integrates {@code setDeltaMovement} on its
     * own; {@link #grappleHookBitesAndReelsThePlayer} therefore asserts the velocity. Here the
     * missing half is supplied by hand - {@code Entity#move(MoverType.SELF, delta)} is exactly what
     * a ticking player's physics would do with that velocity, collisions included - so this covers
     * the one thing the velocity assertion cannot: that the vector chosen actually moves the player
     * at the wall rather than into it or through the floor.
     */
    public static void grappleReelCarriesThePlayerAcrossTheGap(GameTestHelper helper) {
        for (int y = 1; y <= 4; y++) {
            for (int z = 3; z <= 6; z++) {
                helper.setBlock(new BlockPos(7, y, z), Blocks.STONE);
            }
        }

        Vec3 start = new Vec3(1.5D, 1.0D, 4.5D);
        ServerPlayer player = spawnPlayer(helper, start, yawTowards(start, new Vec3(7.0D, 2.5D, 4.5D)));
        double startX = player.getX();
        GrappleBladeItem blade = (GrappleBladeItem) ArsenalFeature.GRAPPLE_BLADE.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(blade));
        blade.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        boolean[] reeled = {false};
        helper.onEachTick(() -> {
            if (GrappleManager.isPulling(player)) {
                reeled[0] = true;
                player.move(MoverType.SELF, player.getDeltaMovement());
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(reeled[0], "the hook should have bitten and started a pull");
            helper.assertFalse(GrappleManager.isPulling(player), "the pull should have finished");
            helper.assertTrue(player.getX() - startX > 3.0D,
                    "the reel should have dragged the player at the wall, but it moved "
                            + (player.getX() - startX) + " blocks");
            helper.assertTrue(player.fallDistance == 0.0F,
                    "the landing after a reel is a soft one");
            release(helper, player);
        });
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Lightning bolts near {@code around}.
     *
     * <p>Deliberately not {@code helper.getEntities(EntityType.LIGHTNING_BOLT)} /
     * {@code assertEntityNotPresent}: those clip to the structure bounds, whose {@code minY} is
     * exactly the top of the structure block - i.e. the floor an impact bolt stands on - and
     * {@code LightningBolt} is registered {@code sized(0.0F, 0.0F)}. A zero-height box sitting on
     * that plane fails {@code AABB#intersects}, which tests {@code maxY > other.minY} strictly, so
     * the structure-bounds query can never see a bolt that struck the floor. A box around the
     * impact block contains it properly.
     */
    private static List<LightningBolt> boltsAround(GameTestHelper helper, BlockPos around) {
        return helper.getEntities(EntityType.LIGHTNING_BOLT, around, 4.0D);
    }

    private static ServerPlayer spawnPlayer(GameTestHelper helper, Vec3 relativePos, float yRot) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = helper.absoluteVec(relativePos);
        player.moveTo(pos.x, pos.y, pos.z, yRot, 0.0F);
        player.setYHeadRot(yRot);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        return player;
    }

    private static void release(GameTestHelper helper, ServerPlayer player) {
        GrappleManager.clear(player);
        GravitySlam.cancel(player);
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static void run(GameTestHelper helper, ServerPlayer player, String command) {
        helper.getLevel().getServer().getCommands()
                .performPrefixedCommand(player.createCommandSourceStack().withPermission(2), command);
    }

    /** Minecraft yaw that points from {@code from} to {@code to}: look.x is -sin(yaw), look.z is cos(yaw). */
    private static float yawTowards(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        return (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
    }

    private static void assertModifier(GameTestHelper helper, ItemStack stack, ResourceLocation id,
                                       double expected, String what) {
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        helper.assertTrue(modifiers != null, what + ": the stack has no attribute modifiers at all");
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            AttributeModifier modifier = entry.modifier();
            if (modifier.id().equals(id)) {
                helper.assertTrue(Math.abs(modifier.amount() - expected) < EPSILON,
                        what + ": expected " + expected + " but was " + modifier.amount());
                helper.assertValueEqual(modifier.operation(), AttributeModifier.Operation.ADD_VALUE,
                        what + " operation");
                return;
            }
        }
        helper.fail(what + ": no modifier with id " + id);
    }

    private static boolean hasEntry(ItemAttributeModifiers modifiers, Holder<Attribute> attribute,
                                    ResourceLocation id) {
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.modifier().id().equals(id) && entry.attribute().value() == attribute.value()) {
                return true;
            }
        }
        return false;
    }

    private ArsenalGameTests() {
    }
}
