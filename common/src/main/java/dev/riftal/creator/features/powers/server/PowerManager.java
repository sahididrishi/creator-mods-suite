package dev.riftal.creator.features.powers.server;

import dev.riftal.creator.core.data.PlayerData;
import dev.riftal.creator.core.net.Payloads;
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

    /** How often the whole-loadout resync sweep runs. Cheap: it only sends when something differs. */
    private static final int SYNC_PERIOD_TICKS = 20;

    /** How often stray {@code NoAI} mobs left by a crash are swept up. */
    private static final int STRAY_SWEEP_PERIOD_TICKS = 200;

    /** Ceiling on ability packets accepted from one player in one tick. */
    private static final int MAX_USES_PER_TICK = 10;

    private static PlayerData<PlayerPowers> data;

    /** Last state actually pushed to each client, so the sweep only sends on a real change. */
    private static final Map<UUID, SyncSnapshot> SYNCED = new HashMap<>();

    private static final Map<UUID, RateWindow> RATE = new HashMap<>();

    private record SyncSnapshot(PlayerPowers powers, ServerPlayer player) {
    }

    private static final class RateWindow {
        private long tick;
        private int count;
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

    private static void store(ServerPlayer player, PlayerPowers powers) {
        if (data != null) {
            data.set(player, powers);
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
        sendCooldown(player, ability.id(), now - Math.max(1, ability.cooldownTicks() - remainingTicks), readyAt, now);
        sync(player);
    }

    // ---------------------------------------------------------------- activation

    /**
     * The keybind path. Validates everything, then fires.
     *
     * <p>A rejection is never announced: the client just gets the true cooldown back so its sweep
     * snaps to the truth. Chat noise during a take is the one thing this whole suite exists to
     * avoid.
     */
    public static UseResult handleUse(ServerPlayer player, ResourceLocation abilityId) {
        if (player.isRemoved() || !(player.level() instanceof ServerLevel level)) {
            return UseResult.CANNOT_USE;
        }
        if (!withinRateLimit(player, level.getGameTime())) {
            return UseResult.CANNOT_USE;
        }

        Optional<Ability> found = AbilityRegistry.get(abilityId);
        if (found.isEmpty()) {
            return UseResult.UNKNOWN;
        }
        Ability ability = found.get();

        long now = level.getGameTime();
        PlayerPowers powers = powersOf(player);
        if (!powers.has(ability.id())) {
            return UseResult.NOT_GRANTED;
        }
        if (!powers.isReady(ability.id(), now)) {
            // Resync, not a new cooldown: rebuild the original window so the client's sweep snaps
            // back to the truth instead of restarting from full.
            long readyAt = powers.readyTick(ability.id(), now);
            sendCooldown(player, ability.id(), readyAt - ability.cooldownTicks(), readyAt, now);
            return UseResult.ON_COOLDOWN;
        }
        if (!ability.canUse(player)) {
            sendCooldown(player, ability.id(), now, now, now);
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
        sendCooldown(player, ability.id(), now, readyAt, now);
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

    // ---------------------------------------------------------------- ground pound impact

    /** Called by {@code ActiveEffects} the tick a pounding player touches down. */
    public static void onPoundImpact(ServerPlayer player, ServerLevel level, long now, double radius) {
        Vec3 origin = player.position();
        BlockPos below = player.blockPosition().below();
        BlockState floor = level.getBlockState(below);

        for (LivingEntity victim : Selection.livingAround(level, origin, radius, player)) {
            double distance = victim.position().distanceTo(origin);
            float falloff = CooldownMath.poundFalloff(distance, radius);
            if (falloff <= 0.0F) {
                continue;
            }
            victim.hurt(level.damageSources().playerAttack(player), 8.0F * falloff);
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
                                     long readyAt, long now) {
        send(player, new CooldownStartPayload(id, startedAt, readyAt, now));
    }

    /**
     * Resyncs anyone whose client is out of date. This is how a join, a respawn and a dimension
     * change all get their HUD back without a loader-specific player event: the snapshot for a
     * fresh {@code ServerPlayer} instance never matches, so the next sweep sends one.
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
     * (Re)starts the per-tick driver. Called every time the server builds its command dispatcher,
     * which is once at start-up and once per {@code /reload} - the one loader-neutral hook that
     * fires on a live server. Cancels the previous task first so a reload never doubles it up.
     */
    public static void startTicking() {
        TickScheduler.cancelAll(PowersFeature.taskTag());
        ActiveEffects.reset();
        SYNCED.clear();
        RATE.clear();
        ActiveEffects.setImpactHandler(PowerManager::onPoundImpact);

        TickScheduler.runRepeating(1, -1, task -> {
            MinecraftServer server = TickScheduler.server();
            if (server == null) {
                return;
            }
            ActiveEffects.tick(server);
            long now = server.overworld().getGameTime();
            if (now % SYNC_PERIOD_TICKS == 0L) {
                syncDirtyPlayers(server);
            }
            if (now % STRAY_SWEEP_PERIOD_TICKS == 0L) {
                ActiveEffects.releaseStrayFrozenMobs(server);
            }
        }).tag(PowersFeature.taskTag());
    }

    private PowerManager() {
    }
}
