package dev.riftal.creator.features.powers.server;

import dev.riftal.creator.core.data.PlayerData;
import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.core.sched.ScheduledTask;
import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.core.util.Selection;
import dev.riftal.creator.features.powers.PowersFeature;
import dev.riftal.creator.features.powers.ability.Ability;
import dev.riftal.creator.features.powers.ability.AbilityContext;
import dev.riftal.creator.features.powers.ability.AbilityFx;
import dev.riftal.creator.features.powers.ability.AbilityRegistry;
import dev.riftal.creator.features.powers.ability.UseResult;
import dev.riftal.creator.features.powers.data.CooldownMath;
import dev.riftal.creator.features.powers.data.PlayerPowers;
import dev.riftal.creator.features.powers.effect.ActiveEffects;
import dev.riftal.creator.features.powers.net.CooldownStartPayload;
import dev.riftal.creator.features.powers.net.SyncPowersPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The server-side brain of the Power Kit: who has what, what is on cooldown, what happens when a
 * key is pressed, and who needs a fresh HUD.
 *
 * <p>Everything here is authoritative. The client predicts a cooldown start so the sweep begins on
 * the same frame as the keypress, but the server is the only thing that decides whether an ability
 * actually fired, and it corrects a wrong prediction with a {@link CooldownStartPayload} carrying
 * the real numbers.
 */
public final class PowerManager {

    /** How often stray {@code NoAI} mobs left by a crash are swept up. */
    private static final int STRAY_SWEEP_PERIOD_TICKS = 200;

    /**
     * Ceiling on ability packets accepted from one player in one tick.
     *
     * <p>No human input can exceed one press per tick; the allowance is only there so the handful
     * of {@code consumeClick()} presses a client drains after a lag spike are all honoured.
     */
    private static final int MAX_USES_PER_TICK = 4;

    /**
     * How long a refused ability is ignored for, after the refusal has been sent back.
     *
     * <p>{@code canUse} is not always cheap - Ender Pull's is a 20-block entity ray-cast - and a
     * refusal starts no cooldown, so a held or scripted key with nothing under the crosshair would
     * otherwise buy an unbounded number of those ray-casts every tick, for ever. The client has
     * already been told the truth by then; a fifth of a second of silence costs a real player
     * nothing.
     */
    private static final int REFUSAL_LOCKOUT_TICKS = 4;

    private static PlayerData<PlayerPowers> data;

    /** Last state actually pushed to each client, so the sweep only sends on a real change. */
    private static final Map<UUID, SyncSnapshot> SYNCED = new HashMap<>();

    private static final Map<UUID, RateWindow> RATE = new HashMap<>();

    /** The per-tick driver, so a {@code /reload} can tell "already running" from "world starting". */
    private static ScheduledTask ticker;

    private record SyncSnapshot(PlayerPowers powers, ServerPlayer player) {
    }

    private static final class RateWindow {
        private long tick;
        private int count;
        /** Ability id to the tick its refusal lockout ends. At most six entries. */
        private final Map<ResourceLocation, Long> refusedUntil = new HashMap<>();
    }

    /** Declares the attachment. Called from {@code PowersFeature#registerContent()}. */
    public static void registerData() {
        if (data == null) {
            data = PlayerData.register(PowersFeature.res(PowersFeature.ATTACHMENT_PATH),
                    PlayerPowers.CODEC, () -> PlayerPowers.EMPTY, true);
        }
    }

    /** The attachment handle. Never null after {@code registerContent()}. */
    public static PlayerData<PlayerPowers> data() {
        return data;
    }

    /** This player's loadout. */
    public static PlayerPowers powersOf(ServerPlayer player) {
        return data == null ? PlayerPowers.EMPTY : data.get(player);
    }

    /**
     * Writes a loadout back, dropping cooldown entries that have already elapsed or belong to an
     * ability the player no longer has. Without the prune the map keeps every id the player was
     * ever granted, for the life of the save file.
     */
    private static void store(ServerPlayer player, PlayerPowers powers) {
        if (data != null) {
            data.set(player, powers.pruned(gameTime(player)));
        }
    }

    /** The tick the whole feature measures cooldowns against. */
    public static long gameTime(ServerPlayer player) {
        return player.level().getGameTime();
    }

    // ---------------------------------------------------------------- grants

    /** Grants one ability into the next free slot. False when it was already held or slots are full. */
    public static boolean grant(ServerPlayer player, Ability ability) {
        PlayerPowers before = powersOf(player);
        PlayerPowers after = before.grant(ability.id());
        if (after == before) {
            return false;
        }
        store(player, after);
        sync(player);
        return true;
    }

    /** Grants everything that still fits, in canonical slot order. Returns how many were added. */
    public static int grantAll(ServerPlayer player) {
        PlayerPowers powers = powersOf(player);
        int added = 0;
        for (Ability ability : AbilityRegistry.ordered()) {
            PlayerPowers next = powers.grant(ability.id());
            if (next != powers) {
                powers = next;
                added++;
            }
        }
        if (added > 0) {
            store(player, powers);
            sync(player);
        }
        return added;
    }

    /** Revokes one ability. False when the player did not have it. */
    public static boolean revoke(ServerPlayer player, Ability ability) {
        PlayerPowers before = powersOf(player);
        PlayerPowers after = before.revoke(ability.id());
        if (after == before) {
            return false;
        }
        store(player, after);
        sync(player);
        return true;
    }

    /** Revokes everything and empties the HUD. Returns how many slots were cleared. */
    public static int clear(ServerPlayer player) {
        PlayerPowers before = powersOf(player);
        int had = before.granted().size();
        if (had == 0 && before.readyAt().isEmpty()) {
            return 0;
        }
        store(player, before.clear());
        // Clearing someone's powers also drops whatever those powers were still doing to them.
        ActiveEffects.clearFor(player);
        sync(player);
        return had;
    }

    // ---------------------------------------------------------------- cooldowns

    /** Makes one ability - or every ability - usable right now. */
    public static void resetCooldowns(ServerPlayer player, Ability only) {
        PlayerPowers before = powersOf(player);
        PlayerPowers after = only == null ? before.clearCooldowns() : before.clearCooldown(only.id());
        if (after != before) {
            store(player, after);
        }
        sync(player);
    }

    /** Forces a specific remaining cooldown, for filming the sweep at a chosen fill. */
    public static void setCooldown(ServerPlayer player, Ability ability, int remainingTicks) {
        long now = gameTime(player);
        long readyAt = CooldownMath.readyAt(now, remainingTicks);
        store(player, powersOf(player).withReadyAt(ability.id(), readyAt));
        sendCooldown(player, ability.id(),
                now - Math.max(1, ability.cooldownTicks() - remainingTicks), readyAt, now, false);
        sync(player);
    }

    // ---------------------------------------------------------------- activation

    /**
     * The keybind path. Validates everything, then fires.
     *
     * <p>A rejection is never announced in chat: the client gets a {@link CooldownStartPayload}
     * flagged {@code refused}, which snaps its sweep back to the truth and shakes the slot. Chat
     * noise during a take is the one thing this whole suite exists to avoid.
     */
    public static UseResult handleUse(ServerPlayer player, ResourceLocation abilityId) {
        if (player.isRemoved() || !(player.level() instanceof ServerLevel level)) {
            return UseResult.CANNOT_USE;
        }
        long now = level.getGameTime();
        if (!withinRateLimit(player, now)) {
            return UseResult.CANNOT_USE;
        }

        Optional<Ability> found = AbilityRegistry.get(abilityId);
        if (found.isEmpty()) {
            return UseResult.UNKNOWN;
        }
        Ability ability = found.get();

        PlayerPowers powers = powersOf(player);
        if (!powers.has(ability.id())) {
            return UseResult.NOT_GRANTED;
        }
        if (!powers.isReady(ability.id(), now)) {
            // Resync, not a new cooldown: rebuild the original window so the client's sweep snaps
            // back to the truth instead of restarting from full.
            long readyAt = powers.readyTick(ability.id(), now);
            sendCooldown(player, ability.id(), readyAt - ability.cooldownTicks(), readyAt, now, true);
            return UseResult.ON_COOLDOWN;
        }
        // Inside the lockout the refusal has already been answered once; do not pay for canUse
        // again and do not send a second identical correction.
        if (inRefusalLockout(player, ability.id(), now)) {
            return UseResult.CANNOT_USE;
        }
        if (!ability.canUse(player)) {
            // startedAt == readyAt == now is "no cooldown at all", and `refused` is what tells the
            // HUD to shake the slot instead of chiming as though something had just come back.
            sendCooldown(player, ability.id(), now, now, now, true);
            lockOutRefusal(player, ability.id(), now);
            return UseResult.CANNOT_USE;
        }

        fire(player, level, ability, now, true);
        return UseResult.ACTIVATED;
    }

    /**
     * The {@code /power use} path: activates regardless of grants and cooldowns, so a take can be
     * triggered on cue. Still respects {@code canUse} - firing a ground pound on the ground would
     * just produce nothing on camera.
     */
    public static UseResult forceUse(ServerPlayer player, Ability ability) {
        if (player.isRemoved() || !(player.level() instanceof ServerLevel level)) {
            return UseResult.CANNOT_USE;
        }
        if (!ability.canUse(player)) {
            return UseResult.CANNOT_USE;
        }
        fire(player, level, ability, level.getGameTime(), false);
        return UseResult.ACTIVATED;
    }

    private static void fire(ServerPlayer player, ServerLevel level, Ability ability, long now,
                             boolean startCooldown) {
        ability.activate(new AbilityContext(player, level, now));
        if (!startCooldown) {
            return;
        }
        long readyAt = CooldownMath.readyAt(now, ability.cooldownTicks());
        store(player, powersOf(player).withReadyAt(ability.id(), readyAt));
        sendCooldown(player, ability.id(), now, readyAt, now, false);
        markSynced(player);
    }

    private static boolean withinRateLimit(ServerPlayer player, long now) {
        RateWindow window = RATE.computeIfAbsent(player.getUUID(), id -> new RateWindow());
        if (window.tick != now) {
            window.tick = now;
            window.count = 0;
        }
        window.count++;
        return window.count <= MAX_USES_PER_TICK;
    }

    /** True while this ability's last refusal is still being served. */
    private static boolean inRefusalLockout(ServerPlayer player, ResourceLocation abilityId, long now) {
        RateWindow window = RATE.get(player.getUUID());
        if (window == null) {
            return false;
        }
        Long until = window.refusedUntil.get(abilityId);
        if (until == null) {
            return false;
        }
        if (now >= until) {
            window.refusedUntil.remove(abilityId);
            return false;
        }
        return true;
    }

    private static void lockOutRefusal(ServerPlayer player, ResourceLocation abilityId, long now) {
        RATE.computeIfAbsent(player.getUUID(), id -> new RateWindow())
                .refusedUntil.put(abilityId, now + REFUSAL_LOCKOUT_TICKS);
    }

    // ---------------------------------------------------------------- ground pound impact

    /** Called by {@code ActiveEffects} the tick a pounding player touches down. */
    public static void onPoundImpact(ServerPlayer player, ServerLevel level, long now, double radius) {
        Vec3 origin = player.position();
        BlockPos below = player.blockPosition().below();
        BlockState floor = level.getBlockState(below);

        for (LivingEntity victim : Selection.livingAround(level, origin, radius, player)) {
            // Armour stands, the creator's own pets and team-mates are scenery, not targets.
            if (!AbilityFx.canAffect(player, victim)) {
                continue;
            }
            double distance = victim.position().distanceTo(origin);
            float falloff = CooldownMath.poundFalloff(distance, radius);
            if (falloff <= 0.0F) {
                continue;
            }
            victim.hurt(level.damageSources().playerAttack(player), 8.0F * falloff);
            if (AbilityFx.isBoss(victim)) {
                continue;
            }
            Vec3 away = victim.position().subtract(origin);
            Vec3 flat = new Vec3(away.x, 0.0D, away.z);
            Vec3 push = (flat.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : flat.normalize()).scale(1.2D);
            AbilityFx.launch(victim, new Vec3(push.x, 0.5D, push.z));
        }

        if (!floor.isAir()) {
            BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, floor);
            Fx.particleRing(level, debris, origin.add(0.0D, 0.1D, 0.0D), 1.6D, 20);
            Fx.particleRing(level, debris, origin.add(0.0D, 0.1D, 0.0D), 3.2D, 40);
        }
        Fx.particles(level, ParticleTypes.EXPLOSION, origin.add(0.0D, 0.2D, 0.0D), 1, 0.0D, 0.0D);
        Fx.sound(level, origin, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
        Fx.sound(level, origin, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9F, 0.6F);

        player.fallDistance = 0.0F;
    }

    // ---------------------------------------------------------------- sync

    /** Pushes this player's whole loadout to their client. */
    public static void sync(ServerPlayer player) {
        if (player.connection == null || player.isRemoved()) {
            return;
        }
        long now = gameTime(player);
        PlayerPowers powers = powersOf(player);
        send(player, SyncPowersPayload.of(powers, now));
        markSynced(player);
    }

    /**
     * One place every S2C packet of this feature goes through.
     *
     * <p>The try/catch is not defensive noise: NeoForge refuses a modded payload on a connection
     * that never negotiated the channel and throws {@code UnsupportedOperationException}
     * ({@code NetworkRegistry#checkPacket}). That happens for a vanilla client on a modded server
     * and for the synthetic player a GameTest builds. Neither is a reason to abort the ability that
     * was being fired - the packet is a HUD mirror, nothing more.
     */
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection == null || player.isRemoved()) {
            return;
        }
        try {
            Payloads.sendToPlayer(player, payload);
        } catch (UnsupportedOperationException notNegotiated) {
            // No channel on the far end. The server state is already correct; drop the mirror.
        }
    }

    private static void markSynced(ServerPlayer player) {
        SYNCED.put(player.getUUID(), new SyncSnapshot(powersOf(player), player));
    }

    private static void sendCooldown(ServerPlayer player, ResourceLocation id, long startedAt,
                                     long readyAt, long now, boolean refused) {
        send(player, new CooldownStartPayload(id, startedAt, readyAt, now, refused));
    }

    /**
     * Resyncs anyone whose client is out of date.
     *
     * <p>Join, respawn and dimension change each push their own sync from
     * {@code PowersPlayerListMixin} / {@code PowersServerPlayerMixin} so the row is on screen on the
     * first frame of the new level. This sweep is the safety net behind them - anything that
     * changes a loadout without going through {@link #sync(ServerPlayer)}, and any player whose
     * hook did not fire. It runs every tick and costs one map lookup plus one record comparison per
     * online player, which is nothing next to the {@code ActiveEffects} tick it sits beside.
     */
    private static void syncDirtyPlayers(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        Set<UUID> online = new HashSet<>(players.size());
        for (ServerPlayer player : players) {
            online.add(player.getUUID());
            SyncSnapshot snapshot = SYNCED.get(player.getUUID());
            // A respawn hands out a brand new ServerPlayer for the same id, so comparing the
            // instance is what catches "this client has a fresh HUD and knows nothing".
            if (snapshot == null
                    || snapshot.player() != player
                    || !snapshot.powers().equals(powersOf(player))) {
                sync(player);
            }
        }
        SYNCED.keySet().retainAll(online);
        RATE.keySet().retainAll(online);
    }

    // ---------------------------------------------------------------- lifecycle

    /**
     * Arms the per-tick driver for a world that is starting.
     *
     * <p>Called every time the server builds its command dispatcher - once at start-up and once per
     * {@code /reload} - because that is the one loader-neutral hook core exposes that fires on a
     * live server. A dispatcher rebuild is <em>not</em> a new world, though, so when the driver is
     * already running this does nothing at all: a {@code /reload} mid-take must not cancel the dash
     * trail that is in flight, drop the dome the creator is standing in, or thaw a frozen crowd.
     * The state wipe belongs to {@link #onServerStopping(MinecraftServer)}.
     */
    public static void startTicking() {
        ActiveEffects.setImpactHandler(PowerManager::onPoundImpact);
        if (ticker != null && !ticker.isDone() && TickScheduler.server() != null) {
            return;
        }

        // A world is starting. TickScheduler#clear() drops tasks without marking them done, so the
        // handle above can be stale after a previous session; clear by tag as well, and wipe any
        // state a crashed session left in the static maps.
        TickScheduler.cancelAll(PowersFeature.taskTag());
        ActiveEffects.reset();
        SYNCED.clear();
        RATE.clear();

        ticker = TickScheduler.runRepeating(1, -1, task -> {
            MinecraftServer server = TickScheduler.server();
            if (server == null) {
                return;
            }
            ActiveEffects.tick(server);
            syncDirtyPlayers(server);
            if (server.overworld().getGameTime() % STRAY_SWEEP_PERIOD_TICKS == 0L) {
                ActiveEffects.releaseStrayFrozenMobs(server);
            }
        }).tag(PowersFeature.taskTag());
    }

    // ---------------------------------------------------------------- player lifecycle

    /**
     * A client just finished joining. Pushing the sync from here rather than waiting for the sweep
     * is what puts the row on screen on the first frame of the world instead of up to a second
     * later.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        sync(player);
    }

    /**
     * A respawn hands out a brand-new {@code ServerPlayer}, so the client's HUD has to be rebuilt
     * and whatever the dead one was carrying has to be handed back.
     */
    public static void onPlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        ActiveEffects.clearFor(oldPlayer);
        SYNCED.remove(oldPlayer.getUUID());
        sync(newPlayer);
    }

    /** Logout: end the dome, drop the pound, thaw this player's mobs, forget their bookkeeping. */
    public static void onPlayerLogout(ServerPlayer player) {
        ActiveEffects.clearFor(player);
        SYNCED.remove(player.getUUID());
        RATE.remove(player.getUUID());
    }

    /**
     * Dimension change. The client rebuilds its level on a respawn packet, so it needs the loadout
     * again - and the fresh {@code serverGameTime} in the sync is what keeps the sweeps drawn
     * against the right clock.
     */
    public static void onDimensionChange(ServerPlayer player) {
        sync(player);
    }

    /**
     * The world is closing. Everything in this feature's static state points at entities of a level
     * that is about to be discarded, so it all goes now rather than when the next world opens:
     * domes hand their modifiers back to players who still exist, frozen mobs get their real AI
     * flags back before they are written to disk, and nothing holds a {@code ServerLevel} alive.
     */
    public static void onServerStopping(MinecraftServer server) {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        TickScheduler.cancelAll(PowersFeature.taskTag());
        ActiveEffects.reset();
        SYNCED.clear();
        RATE.clear();
    }

    private PowerManager() {
    }
}
