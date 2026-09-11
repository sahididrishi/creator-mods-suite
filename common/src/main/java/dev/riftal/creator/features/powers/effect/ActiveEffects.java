package dev.riftal.creator.features.powers.effect;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.core.util.MathUtil;
import dev.riftal.creator.core.util.Selection;
import dev.riftal.creator.features.powers.PowersFeature;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every non-persistent, timed, server-side state the abilities create: dash invulnerability
 * windows, live Shield Domes, in-flight Ground Pounds and frozen mobs.
 *
 * <p>None of it is saved. A relog ends a dome and thaws that player's mobs, which is exactly what
 * the plan asks for - the only thing that survives a restart is the cooldown map in
 * {@code PlayerPowers}.
 *
 * <p>Driven once per tick from {@code PowerManager}'s scheduled task. The work per tick is
 * proportional to the number of <em>live</em> effects, not to the number of entities in the world:
 * with nothing active this class does nothing at all.
 */
public final class ActiveEffects {

    /** Entity tag put on frozen mobs so a server crash mid-freeze can still be undone on load. */
    public static final String FROZEN_TAG = "creator_powers_frozen";

    /**
     * The pre-freeze AI flags, written onto the mob itself next to {@link #FROZEN_TAG}.
     *
     * <p>{@code NoAI} and {@code NoGravity} are saved to entity NBT, so a mob frozen when the server
     * died comes back brain-dead with only its tags to say what it used to be. Recording the two
     * flags as tags is what lets {@link #releaseStrayFrozenMobs} hand back the <em>original</em>
     * values instead of hard-coding "false" onto a build prop that was deliberately {@code NoAI}.
     */
    public static final String HAD_NO_AI_TAG = "creator_powers_had_noai";
    public static final String HAD_NO_GRAVITY_TAG = "creator_powers_had_nogravity";

    /** Dome radius used for the particle shell and the projectile void. */
    public static final double DOME_RADIUS = 4.0D;
    private static final double DOME_VOID_RADIUS = 4.5D;
    private static final int DOME_SHELL_POINTS = 48;
    private static final int DOME_SHELL_PERIOD = 4;

    private static final double GROUND_POUND_RADIUS = 6.0D;

    /** Transient attribute modifiers the dome adds. Fixed ids so expiry can remove them by name. */
    public static final ResourceLocation DOME_ARMOR_ID =
            ResourceLocation.fromNamespaceAndPath(PowersFeature.NAMESPACE, "dome_armor");
    public static final ResourceLocation DOME_TOUGHNESS_ID =
            ResourceLocation.fromNamespaceAndPath(PowersFeature.NAMESPACE, "dome_toughness");

    /**
     * Id of the {@code generic.max_absorption} headroom the dome needs to hand over its four
     * golden hearts.
     *
     * <p>1.21 gates absorption behind that attribute: {@code Attributes.MAX_ABSORPTION} is a
     * {@code RangedAttribute} whose default is <b>0.0</b>, and
     * {@code LivingEntity#setAbsorptionAmount} is
     * {@code internalSetAbsorptionAmount(Mth.clamp(amount, 0.0F, getMaxAbsorption()))}. Without a
     * modifier on that attribute the dome's {@code setAbsorptionAmount(8)} is silently clamped to
     * zero and the dome grants nothing at all. Vanilla's own Absorption effect works exactly this
     * way - it carries a {@code minecraft:effect.absorption} modifier on this attribute - so this
     * is the supported route, not a workaround. The modifier is transient: it is never written to
     * the player file.
     */
    public static final ResourceLocation DOME_ABSORPTION_ID =
            ResourceLocation.fromNamespaceAndPath(PowersFeature.NAMESPACE, "dome_absorption");

    private static final double DOME_ARMOR_BONUS = 10.0D;
    private static final double DOME_TOUGHNESS_BONUS = 4.0D;

    private static final Map<UUID, Long> DASH_INVULNERABLE_UNTIL = new LinkedHashMap<>();
    private static final Map<UUID, DomeState> DOMES = new LinkedHashMap<>();
    private static final Map<UUID, PoundState> POUNDS = new LinkedHashMap<>();
    private static final Map<UUID, FrozenMob> FROZEN = new LinkedHashMap<>();

    /** Called when a pound lands. Set by {@code PowerManager} so this class stays ability-agnostic. */
    public interface ImpactHandler {
        void onPoundImpact(ServerPlayer player, ServerLevel level, long gameTime, double radius);
    }

    private static ImpactHandler impactHandler;

    public static void setImpactHandler(ImpactHandler handler) {
        impactHandler = handler;
    }

    // ---------------------------------------------------------------- dash

    /** Grants melee i-frames until {@code untilTick}. */
    public static void startDashInvulnerability(ServerPlayer player, long untilTick) {
        DASH_INVULNERABLE_UNTIL.put(player.getUUID(), untilTick);
    }

    /** True while the player is inside a dash i-frame window. */
    public static boolean dashInvulnerable(UUID playerId, long now) {
        Long until = DASH_INVULNERABLE_UNTIL.get(playerId);
        return until != null && now < until;
    }

    // ---------------------------------------------------------------- dome

    /** Raises a dome and applies its absorption, resistance and armour modifiers. */
    public static void startDome(ServerPlayer player, long now, int durationTicks, float absorption) {
        endDome(player, false);

        float before = player.getAbsorptionAmount();
        float target = Math.max(before, absorption);
        // The ceiling has to go up BEFORE the hearts go on, or setAbsorptionAmount clamps them
        // away - see DOME_ABSORPTION_ID.
        addDomeModifier(player, Attributes.MAX_ABSORPTION, DOME_ABSORPTION_ID, absorption);
        player.setAbsorptionAmount(target);
        // Read back rather than trusting `target`, so expiry hands back exactly what landed.
        float given = Math.max(0.0F, player.getAbsorptionAmount() - before);

        addDomeModifier(player, Attributes.ARMOR, DOME_ARMOR_ID, DOME_ARMOR_BONUS);
        addDomeModifier(player, Attributes.ARMOR_TOUGHNESS, DOME_TOUGHNESS_ID, DOME_TOUGHNESS_BONUS);

        DOMES.put(player.getUUID(), new DomeState(player, now + durationTicks, given));
    }

    /** True while {@code playerId} is under a live dome. */
    public static boolean domeActive(UUID playerId, long now) {
        DomeState dome = DOMES.get(playerId);
        return dome != null && now < dome.until();
    }

    /** Ticks of dome left, 0 when there is none. */
    public static int domeRemaining(UUID playerId, long now) {
        DomeState dome = DOMES.get(playerId);
        if (dome == null || now >= dome.until()) {
            return 0;
        }
        return (int) (dome.until() - now);
    }

    /** Drops the dome, taking back exactly the absorption it gave. */
    public static void endDome(ServerPlayer player, boolean playSound) {
        DomeState dome = DOMES.remove(player.getUUID());
        if (dome == null) {
            return;
        }
        if (dome.absorptionGiven() > 0.0F) {
            float now = player.getAbsorptionAmount();
            player.setAbsorptionAmount(Math.max(0.0F, now - dome.absorptionGiven()));
        }
        // Only now: dropping the ceiling re-clamps whatever absorption is left (LivingEntity
        // #onAttributeUpdated), so it must come off AFTER the give-back or it would also eat a
        // golden apple's hearts - which is the whole point of tracking absorptionGiven.
        removeDomeModifier(player, Attributes.MAX_ABSORPTION, DOME_ABSORPTION_ID);
        removeDomeModifier(player, Attributes.ARMOR, DOME_ARMOR_ID);
        removeDomeModifier(player, Attributes.ARMOR_TOUGHNESS, DOME_TOUGHNESS_ID);
        if (playSound && player.level() instanceof ServerLevel level) {
            Fx.sound(level, player.position(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7F, 1.2F);
        }
    }

    private static void addDomeModifier(ServerPlayer player,
                                        Holder<Attribute> attribute,
                                        ResourceLocation id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.addOrUpdateTransientModifier(
                    new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void removeDomeModifier(ServerPlayer player,
                                           Holder<Attribute> attribute,
                                           ResourceLocation id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    // ---------------------------------------------------------------- ground pound

    /** Phase A: the player has been popped up and is waiting to slam. */
    public static void startPound(ServerPlayer player, long expiresAt) {
        POUNDS.put(player.getUUID(), new PoundState(player, false, expiresAt));
    }

    /** Phase B: the player is now falling and the next landing is an impact. */
    public static void armPound(ServerPlayer player, long expiresAt) {
        POUNDS.put(player.getUUID(), new PoundState(player, true, expiresAt));
    }

    /** True while a pound is queued or falling. */
    public static boolean pounding(UUID playerId) {
        return POUNDS.containsKey(playerId);
    }

    /** Abandons a pound without an impact. */
    public static void cancelPound(UUID playerId) {
        POUNDS.remove(playerId);
    }

    // ---------------------------------------------------------------- mob freeze

    /**
     * Freezes one mob until {@code until}, remembering the AI flags it had. A mob that is already
     * frozen just has its timer extended - and changes hands, so the newest caster is the one whose
     * logout thaws it.
     *
     * @param owner the player whose Mob Freeze this is
     */
    public static void freeze(Mob mob, long until, UUID owner) {
        FrozenMob existing = FROZEN.get(mob.getUUID());
        if (existing != null) {
            FROZEN.put(mob.getUUID(), new FrozenMob(mob, existing.hadNoAi(), existing.hadNoGravity(),
                    Math.max(existing.until(), until), owner));
            return;
        }
        boolean hadNoAi = mob.isNoAi();
        boolean hadNoGravity = mob.isNoGravity();
        FROZEN.put(mob.getUUID(), new FrozenMob(mob, hadNoAi, hadNoGravity, until, owner));
        mob.addTag(FROZEN_TAG);
        // Written onto the mob as well as into the map: the map does not survive a crash, the tags
        // do - see HAD_NO_AI_TAG.
        if (hadNoAi) {
            mob.addTag(HAD_NO_AI_TAG);
        }
        if (hadNoGravity) {
            mob.addTag(HAD_NO_GRAVITY_TAG);
        }
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.hurtMarked = true;
    }

    /** True while this mob is held by Mob Freeze. */
    public static boolean isFrozen(UUID mobId) {
        return FROZEN.containsKey(mobId);
    }

    /** How many mobs this session is currently holding frozen. Asserted by the GameTests. */
    public static int frozenCount() {
        return FROZEN.size();
    }

    private static void thaw(FrozenMob frozen, boolean playSound) {
        Mob mob = frozen.mob();
        mob.setNoAi(frozen.hadNoAi());
        mob.setNoGravity(frozen.hadNoGravity());
        mob.setTicksFrozen(0);
        mob.removeTag(FROZEN_TAG);
        mob.removeTag(HAD_NO_AI_TAG);
        mob.removeTag(HAD_NO_GRAVITY_TAG);
        if (playSound && !mob.isRemoved() && mob.level() instanceof ServerLevel level) {
            Fx.particles(level, ParticleTypes.SNOWFLAKE, mob.position().add(0.0D, mob.getBbHeight() * 0.5D, 0.0D),
                    8, 0.35D, 0.01D);
            Fx.sound(level, mob.position(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.HOSTILE, 0.5F, 1.4F);
        }
    }

    // ---------------------------------------------------------------- lifecycle

    /**
     * Single decision point for "does this damage get through". Called from
     * {@code PowersServerPlayerMixin}; the caller has already checked the feature toggle.
     */
    public static boolean shouldCancelDamage(ServerPlayer player, DamageSource source, long now) {
        UUID id = player.getUUID();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (dashInvulnerable(id, now) && isMelee(source)) {
            return true;
        }
        if (domeActive(id, now)
                && (source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypeTags.IS_EXPLOSION))) {
            return true;
        }
        return false;
    }

    /**
     * "Melee only", as the plan's dash row spells it: a hit delivered in person by something
     * standing next to you.
     *
     * <p>Stated positively (a living direct attacker) rather than as a blacklist, so the eight
     * ticks of dash cover can never quietly become better anti-arrow protection than the Shield
     * Dome. Arrows, explosions, fire, fall, magic, drowning and starvation all land as normal -
     * the window is there to carry the creator through a mob pile, nothing more.
     */
    private static boolean isMelee(DamageSource source) {
        if (source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return false;
        }
        return source.getDirectEntity() instanceof LivingEntity;
    }

    /** Runs once per server tick. Cheap when nothing is active. */
    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        long now = server.overworld().getGameTime();

        tickDashWindows(now);
        tickDomes(now);
        tickPounds(now);
        tickFrozen(now);
    }

    private static void tickDashWindows(long now) {
        DASH_INVULNERABLE_UNTIL.values().removeIf(until -> now >= until);
    }

    private static void tickDomes(long now) {
        if (DOMES.isEmpty()) {
            return;
        }
        for (UUID id : new ArrayList<>(DOMES.keySet())) {
            DomeState dome = DOMES.get(id);
            if (dome == null) {
                continue;
            }
            ServerPlayer player = dome.player();
            // A logged-out player is already discarded and saved; there is nothing left to take
            // back off them, so the dome is simply forgotten.
            if (player.isRemoved() || !(player.level() instanceof ServerLevel level)) {
                DOMES.remove(id);
                continue;
            }
            if (now >= dome.until()) {
                endDome(player, true);
                continue;
            }
            voidProjectiles(level, player);
            if (now % DOME_SHELL_PERIOD == 0L) {
                drawDomeShell(level, player.position().add(0.0D, 1.0D, 0.0D));
            }
        }
    }

    private static void voidProjectiles(ServerLevel level, ServerPlayer player) {
        Vec3 centre = player.position().add(0.0D, 1.0D, 0.0D);
        List<Projectile> incoming = level.getEntitiesOfClass(Projectile.class,
                MathUtil.boxAround(centre, DOME_VOID_RADIUS),
                projectile -> projectile.isAlive() && projectile.getOwner() != player);
        for (Projectile projectile : incoming) {
            if (projectile.position().distanceTo(centre) > DOME_VOID_RADIUS) {
                continue;
            }
            Vec3 toCentre = centre.subtract(projectile.position());
            if (projectile.getDeltaMovement().dot(toCentre) <= 0.0D) {
                continue;
            }
            Fx.particles(level, ParticleTypes.ENCHANTED_HIT, projectile.position(), 4, 0.15D, 0.05D);
            Fx.sound(level, projectile.position(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.6F, 1.6F);
            projectile.discard();
        }
    }

    /** Evenly spread points on a sphere (Fibonacci lattice) - no clustering at the poles. */
    private static void drawDomeShell(ServerLevel level, Vec3 centre) {
        double golden = Math.PI * (3.0D - Math.sqrt(5.0D));
        for (int i = 0; i < DOME_SHELL_POINTS; i++) {
            double y = 1.0D - (i / (double) (DOME_SHELL_POINTS - 1)) * 2.0D;
            double radiusAtY = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
            double theta = golden * i;
            Vec3 point = centre.add(Math.cos(theta) * radiusAtY * DOME_RADIUS,
                    y * DOME_RADIUS,
                    Math.sin(theta) * radiusAtY * DOME_RADIUS);
            level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void tickPounds(long now) {
        if (POUNDS.isEmpty()) {
            return;
        }
        for (UUID id : new ArrayList<>(POUNDS.keySet())) {
            PoundState state = POUNDS.get(id);
            if (state == null) {
                continue;
            }
            ServerPlayer player = state.player();
            if (player.isRemoved() || !(player.level() instanceof ServerLevel level)) {
                POUNDS.remove(id);
                continue;
            }
            if (now >= state.expiresAt()) {
                POUNDS.remove(id);
                continue;
            }
            if (state.slamming() && (player.onGround() || player.isInWater())) {
                POUNDS.remove(id);
                if (impactHandler != null) {
                    impactHandler.onPoundImpact(player, level, now, GROUND_POUND_RADIUS);
                }
            }
        }
    }

    private static void tickFrozen(long now) {
        if (FROZEN.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, FrozenMob>> it = FROZEN.entrySet().iterator();
        while (it.hasNext()) {
            FrozenMob frozen = it.next().getValue();
            Mob mob = frozen.mob();
            Entity.RemovalReason removal = mob.getRemovalReason();
            if (removal != null) {
                it.remove();
                // A mob that died takes its flags with it. A mob that is merely being unloaded is
                // about to be written to disk, so it has to get its real NoAI/NoGravity back first
                // or it wakes up brain-dead in a chunk nobody is standing in.
                if (!removal.shouldDestroy()) {
                    thaw(frozen, false);
                }
                continue;
            }
            if (!mob.isAlive()) {
                it.remove();
                continue;
            }
            if (now >= frozen.until()) {
                it.remove();
                thaw(frozen, true);
                continue;
            }
            // Pin them in place so a hit cannot knock a frozen mob around, and borrow the vanilla
            // powder-snow shiver for the visual. 139 is one below getTicksRequiredToFreeze(), so
            // vanilla never starts dealing freeze damage.
            mob.setDeltaMovement(Vec3.ZERO);
            mob.setTicksFrozen(139);
            if (now % 10L == 0L && mob.level() instanceof ServerLevel level) {
                Fx.particles(level, ParticleTypes.SNOWFLAKE,
                        mob.position().add(0.0D, mob.getBbHeight() * 0.6D, 0.0D), 6, 0.3D, 0.005D);
            }
        }
    }

    /**
     * Safety net for the {@code NoAI}-persists-to-NBT trap: if the server died while mobs were
     * frozen they come back tagged and brain-dead forever. Any tagged mob near a player that this
     * session does not know about is released. Called on a slow schedule, never every tick.
     */
    public static void releaseStrayFrozenMobs(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            List<Mob> stray = Selection.around(level, Mob.class, player.position(), 32.0D,
                    mob -> mob.getTags().contains(FROZEN_TAG) && !FROZEN.containsKey(mob.getUUID()));
            for (Mob mob : stray) {
                // The flags the mob had before it was ever frozen, recorded as tags at freeze time.
                // A build prop that was deliberately NoAI stays NoAI.
                mob.setNoAi(mob.getTags().contains(HAD_NO_AI_TAG));
                mob.setNoGravity(mob.getTags().contains(HAD_NO_GRAVITY_TAG));
                mob.setTicksFrozen(0);
                mob.removeTag(FROZEN_TAG);
                mob.removeTag(HAD_NO_AI_TAG);
                mob.removeTag(HAD_NO_GRAVITY_TAG);
            }
        }
    }

    /**
     * Ends every effect this player owns: the dome comes off with its buffs, an in-flight pound is
     * abandoned, the i-frame window is dropped and every mob this player froze is thawed.
     *
     * <p>Called on logout, on death and on {@code /power clear} - see
     * {@code PowersPlayerListMixin}, which is what makes the first two happen.
     */
    public static void clearFor(ServerPlayer player) {
        UUID id = player.getUUID();
        DASH_INVULNERABLE_UNTIL.remove(id);
        POUNDS.remove(id);
        endDome(player, false);
        thawOwnedBy(id);
    }

    /** Thaws every mob frozen by one player, leaving everybody else's freezes alone. */
    private static void thawOwnedBy(UUID owner) {
        if (FROZEN.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, FrozenMob>> it = FROZEN.entrySet().iterator();
        while (it.hasNext()) {
            FrozenMob frozen = it.next().getValue();
            if (owner.equals(frozen.owner())) {
                it.remove();
                thaw(frozen, false);
            }
        }
    }

    /**
     * Thaws everything, drops every dome and forgets every timer. Called when a world stops, and
     * defensively when one starts.
     *
     * <p>Every dome is ended through {@link #endDome} rather than dropped from the map: the +10
     * armour, the +4 toughness and the max-absorption headroom are transient attribute modifiers on
     * a live player, and forgetting the dome without removing them leaves the creator buffed for the
     * rest of the session.
     */
    public static void reset() {
        for (FrozenMob frozen : new ArrayList<>(FROZEN.values())) {
            thaw(frozen, false);
        }
        FROZEN.clear();
        for (DomeState dome : new ArrayList<>(DOMES.values())) {
            endDome(dome.player(), false);
        }
        DOMES.clear();
        DASH_INVULNERABLE_UNTIL.clear();
        POUNDS.clear();
    }

    private ActiveEffects() {
    }
}
