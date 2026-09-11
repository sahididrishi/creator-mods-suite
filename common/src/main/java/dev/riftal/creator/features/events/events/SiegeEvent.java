package dev.riftal.creator.features.events.events;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.core.util.Titles;
import dev.riftal.creator.features.events.EventsFeature;
import dev.riftal.creator.features.events.api.EventContext;
import dev.riftal.creator.features.events.api.EventPhase;
import dev.riftal.creator.features.events.api.StopReason;
import dev.riftal.creator.features.events.api.WorldEvent;
import dev.riftal.creator.features.events.hooks.EventHooks;
import dev.riftal.creator.features.events.siege.SiegeWave;
import dev.riftal.creator.features.events.siege.SiegeWaves;
import dev.riftal.creator.features.events.util.SpawnRing;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@code siege} - waves of hostiles converge on the director from every direction, counted off on a
 * notched boss bar, ending in a reward drop.
 *
 * <p>The wave state machine lives inside one open {@code waves} phase rather than one phase per
 * wave, because the wave count is data driven and {@link WorldEvent#phases()} is fixed.
 *
 * <p>Ownership of the spawned mobs is tracked as a set of UUIDs rather than re-derived from a full
 * {@code getAllEntities()} sweep twice a second: on a dressed recording world that sweep walks
 * thousands of item frames and armour stands. The {@code creator_events_siege} tag is kept purely so
 * a resume can rebuild the set in one pass.
 */
public final class SiegeEvent implements WorldEvent {

    /** Ticks between "wave cleared" and the next wave spawning. */
    public static final int BREATHER_TICKS = 100;

    /** A wave that somehow cannot be finished still ends after this long. */
    public static final int WAVE_TIMEOUT_TICKS = 4000;

    /** Nothing at all has died in this long: the wave is stuck, end it rather than burn the timeout. */
    public static final int WAVE_STALL_TICKS = 600;

    /** Boss bar visibility radius, matching vanilla raids. */
    private static final double BAR_RADIUS = 96.0D;

    private static final int RETARGET_PERIOD = 20;
    private static final int COUNT_PERIOD = 10;
    private static final int SPAWN_ATTEMPTS = 12;

    /** How far the surface may be from the director's own Y before we search for a floor instead. */
    private static final int SURFACE_TOLERANCE = 6;

    /** How far up and down from the director's Y the floor search looks. */
    private static final int VERTICAL_SEARCH = 16;

    /** Reward table, filled by {@code data/creator_events/loot_table/siege_reward.json}. */
    public static final ResourceKey<LootTable> SIEGE_REWARD =
            ResourceKey.create(Registries.LOOT_TABLE,
                    ResourceLocation.fromNamespaceAndPath(EventsFeature.NAMESPACE, "siege_reward"));

    private List<SiegeWave> waves = SiegeWaves.defaults();
    private double difficultyFactor = SiegeWaves.NORMAL_FACTOR;
    private double minRadius = 24.0D;
    private double maxRadius = 40.0D;
    private boolean wantBar = true;

    private final Set<UUID> spawned = new LinkedHashSet<>();

    private ServerBossEvent bar;
    private int wave;
    private int waveTotalMobs;
    private int alive;
    private int breather;
    private int waveTicks;
    private boolean cleared;

    @Override
    public String id() {
        return "siege";
    }

    @Override
    public List<EventPhase> phases() {
        return List.of(
                EventPhase.ticks("prepare", 60),
                EventPhase.open("waves"),
                EventPhase.ticks("victory", 100));
    }

    @Override
    public void onStart(EventContext ctx, boolean resumed) {
        waves = EventsFeature.siegeWaves(ctx.server());
        int requested = ctx.options().getInt("waves", waves.size(), 1, waves.size());
        if (requested < waves.size()) {
            waves = waves.subList(0, requested);
        }
        difficultyFactor = SiegeWaves.factorForDifficulty(ctx.level().getDifficulty().getKey());
        maxRadius = ctx.options().getDouble("radius", 40.0D, 6.0D, 128.0D);
        minRadius = Math.max(4.0D, maxRadius * 0.6D);
        // The plan's command table: "boss bar for siege stays unless option bossbar=false".
        wantBar = ctx.options().getBoolean("bossbar", true);

        if (wantBar) {
            bar = new ServerBossEvent(barName(), BossEvent.BossBarColor.RED,
                    BossEvent.BossBarOverlay.NOTCHED_10);
            bar.setProgress(1.0F);
            updateBarPlayers(ctx);
        }

        if (resumed) {
            // The one full sweep this event does: rebuild the ownership set from the tag that
            // survived the restart, then never scan the level again.
            recoverSpawnedFromTags(ctx);
            alive = countAlive(ctx);
            if (waveTotalMobs <= 0) {
                waveTotalMobs = Math.max(alive, 1);
            }
            if (bar != null) {
                bar.setName(barName());
            }
        }
    }

    @Override
    public void onPhaseStart(EventContext ctx, EventPhase phase, int phaseIndex) {
        switch (phase.id()) {
            case "prepare" -> {
                Fx.sound(ctx.level(), ctx.focus(), EventsFeature.siegeHorn(), SoundSource.HOSTILE,
                        3.0F, 1.0F);
                Component title = Component.translatable("title.creator_events.siege")
                        .withStyle(ChatFormatting.DARK_RED);
                for (ServerPlayer player : ctx.players()) {
                    Titles.show(player, title,
                            Component.translatable("title.creator_events.siege.sub", waves.size()));
                }
            }
            case "waves" -> startWave(ctx, 1);
            case "victory" -> victory(ctx);
            default -> {
            }
        }
    }

    @Override
    public void tick(EventContext ctx, EventPhase phase, int phaseTick) {
        if (bar != null && phaseTick % COUNT_PERIOD == 0) {
            updateBarPlayers(ctx);
        }
        if (!"waves".equals(phase.id())) {
            return;
        }
        waveTicks++;

        if (breather > 0) {
            if (--breather == 0 && !cleared) {
                startWave(ctx, wave + 1);
            }
            return;
        }

        if (waveTicks % COUNT_PERIOD == 0) {
            alive = countAlive(ctx);
            if (bar != null) {
                bar.setProgress(waveTotalMobs <= 0 ? 0.0F
                        : Math.max(0.0F, Math.min(1.0F, (float) alive / waveTotalMobs)));
            }
        }
        if (waveTicks % RETARGET_PERIOD == 0) {
            converge(ctx);
        }

        boolean waveOver = (waveTicks > 40 && alive <= 0) || waveTicks > WAVE_TIMEOUT_TICKS;
        if (!waveOver && waveTicks > WAVE_STALL_TICKS && waveTotalMobs > 0
                && alive >= waveTotalMobs) {
            // Half a minute and not one casualty: nothing can reach anybody (a creative-only take,
            // or a wave that landed behind a wall). Move on rather than sit on a frozen HUD line
            // for the full 200-second timeout, five times over.
            LOG.warn("[events] siege wave {}/{} stalled with {} alive; advancing", wave,
                    waves.size(), alive);
            waveOver = true;
        }
        if (!waveOver) {
            return;
        }
        if (wave >= waves.size()) {
            cleared = true;
        } else {
            breather = BREATHER_TICKS;
            Component next = Component.translatable("title.creator_events.wave", wave + 1, waves.size())
                    .withStyle(ChatFormatting.GOLD);
            for (ServerPlayer player : ctx.players()) {
                Titles.show(player, next, Component.empty(), 5, 30, 10);
            }
        }
    }

    @Override
    public boolean isPhaseComplete(EventContext ctx, EventPhase phase, int phaseTick) {
        if ("waves".equals(phase.id())) {
            return cleared;
        }
        return WorldEvent.super.isPhaseComplete(ctx, phase, phaseTick);
    }

    @Override
    public void onStop(EventContext ctx, StopReason reason) {
        if (bar != null) {
            bar.removeAllPlayers();
            bar.setVisible(false);
            bar = null;
        }
        if (ctx == null) {
            spawned.clear();
            return;
        }
        for (Entity entity : taggedMobs(ctx)) {
            entity.discard();
        }
        spawned.clear();
    }

    @Override
    public void onPlayerJoin(EventContext ctx, ServerPlayer player) {
        if (bar != null) {
            bar.addPlayer(player);
        }
    }

    @Override
    public int wave() {
        return wave;
    }

    @Override
    public int waveTotal() {
        return waves.size();
    }

    @Override
    public int alive() {
        return alive;
    }

    @Override
    public float progress(EventContext ctx, EventPhase phase, int phaseTick) {
        if ("waves".equals(phase.id())) {
            if (waves.isEmpty()) {
                return 1.0F;
            }
            float wavesDone = Math.max(0, wave - 1);
            float inWave = waveTotalMobs <= 0 ? 1.0F
                    : 1.0F - Math.min(1.0F, (float) alive / waveTotalMobs);
            return Math.min(1.0F, (wavesDone + inWave) / waves.size());
        }
        return WorldEvent.super.progress(ctx, phase, phaseTick);
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt("wave", wave);
        tag.putInt("wave_total_mobs", waveTotalMobs);
        tag.putInt("breather", breather);
        tag.putInt("wave_ticks", waveTicks);
        tag.putBoolean("cleared", cleared);
        tag.putBoolean("bossbar", wantBar);
        ListTag ids = new ListTag();
        for (UUID id : spawned) {
            ids.add(NbtUtils.createUUID(id));
        }
        tag.put("spawned", ids);
    }

    @Override
    public void load(CompoundTag tag) {
        wave = tag.getInt("wave");
        waveTotalMobs = tag.getInt("wave_total_mobs");
        breather = tag.getInt("breather");
        waveTicks = tag.getInt("wave_ticks");
        cleared = tag.getBoolean("cleared");
        wantBar = !tag.contains("bossbar") || tag.getBoolean("bossbar");
        spawned.clear();
        for (Tag element : tag.getList("spawned", Tag.TAG_INT_ARRAY)) {
            spawned.add(NbtUtils.loadUUID(element));
        }
    }

    private Component barName() {
        return Component.translatable("bossbar.creator_events.siege",
                Math.max(1, wave), Math.max(1, waves.size()));
    }

    /**
     * Adds nearby players to the bar and drops everybody else - including players who have gone
     * offline, which the live player list no longer mentions at all. Left in, a disconnected
     * {@code ServerPlayer} takes a packet on every {@code setProgress} for the rest of the event.
     */
    private void updateBarPlayers(EventContext ctx) {
        if (bar == null) {
            return;
        }
        List<ServerPlayer> here = ctx.players();
        for (ServerPlayer member : new ArrayList<>(bar.getPlayers())) {
            if (!here.contains(member)) {
                bar.removePlayer(member);
            }
        }
        Vec3 focus = ctx.focus();
        for (ServerPlayer player : here) {
            boolean near = player.position().distanceToSqr(focus) <= BAR_RADIUS * BAR_RADIUS;
            if (near) {
                bar.addPlayer(player);
            } else {
                bar.removePlayer(player);
            }
        }
    }

    private void startWave(EventContext ctx, int number) {
        wave = Math.max(1, Math.min(number, waves.size()));
        waveTicks = 0;
        breather = 0;
        SiegeWave definition = waves.get(wave - 1);
        waveTotalMobs = 0;

        ServerLevel level = ctx.level();
        Vec3 focus = ctx.focus();
        RandomSource random = ctx.random();
        for (SiegeWave.Spawn spawn : definition.spawns()) {
            ResourceLocation id = ResourceLocation.tryParse(spawn.entityId());
            if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                LOG.warn("[events] siege wave {} names unknown entity '{}'", wave, spawn.entityId());
                continue;
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            int count = SiegeWaves.scale(spawn.count(), difficultyFactor);
            for (int i = 0; i < count; i++) {
                if (spawnOne(level, type, focus, random)) {
                    waveTotalMobs++;
                }
            }
        }
        alive = countAlive(ctx);
        if (bar != null) {
            bar.setName(barName());
            bar.setProgress(1.0F);
        }
        Fx.sound(level, focus, EventsFeature.siegeHorn(), SoundSource.HOSTILE, 2.0F,
                1.0F + wave * 0.05F);
        if (waveTotalMobs == 0) {
            // Silence here used to march the whole siege through five "cleared" waves in 35
            // seconds with nothing on screen. Say so, on camera and in the log.
            LOG.warn("[events] siege wave {}/{} found nowhere to spawn within {}..{} blocks of {}",
                    wave, waves.size(), (int) minRadius, (int) maxRadius, ctx.originPos());
            Component warning = Component.translatable("title.creator_events.siege_no_room")
                    .withStyle(ChatFormatting.RED);
            for (ServerPlayer player : ctx.players()) {
                player.displayClientMessage(warning, true);
            }
            return;
        }
        LOG.info("[events] siege wave {}/{} spawned {} mobs", wave, waves.size(), waveTotalMobs);
    }

    private boolean spawnOne(ServerLevel level, EntityType<?> type, Vec3 focus, RandomSource random) {
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            Vec3 flat = SpawnRing.sample(random, focus, minRadius, maxRadius);
            BlockPos column = BlockPos.containing(flat.x, focus.y, flat.z);
            if (!level.hasChunkAt(column)) {
                continue;
            }
            BlockPos spot = spawnPosition(level, type, column);
            boolean last = attempt == SPAWN_ATTEMPTS - 1;
            if (!last && !SpawnPlacements.isSpawnPositionOk(type, level, spot)) {
                continue;
            }
            Entity spawned = type.spawn(level, spot, MobSpawnType.EVENT);
            if (spawned == null) {
                continue;
            }
            prepareMob(spawned);
            return true;
        }
        return false;
    }

    /**
     * Where to put one mob in {@code column}.
     *
     * <p>The surface heightmap alone is wrong whenever the director is not standing on the surface:
     * underground it drops the wave 50 blocks above the player's head, and in the Nether
     * {@code MOTION_BLOCKING_NO_LEAVES} resolves to the bedrock ceiling, so the whole siege lands on
     * the roof. When the surface is nowhere near the director's own Y, walk down from their level
     * looking for a floor instead.
     */
    private BlockPos spawnPosition(ServerLevel level, EntityType<?> type, BlockPos column) {
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        if (Math.abs(surface.getY() - column.getY()) <= SURFACE_TOLERANCE) {
            return surface;
        }
        int top = Mth.clamp(surface.getY(), column.getY() - VERTICAL_SEARCH,
                column.getY() + VERTICAL_SEARCH);
        int bottom = Math.max(level.getMinBuildHeight() + 1, column.getY() - VERTICAL_SEARCH);
        for (int y = top; y >= bottom; y--) {
            BlockPos candidate = new BlockPos(column.getX(), y, column.getZ());
            if (SpawnPlacements.isSpawnPositionOk(type, level, candidate)) {
                return candidate;
            }
        }
        return surface;
    }

    private void prepareMob(Entity entity) {
        entity.addTag(EventHooks.TAG_SIEGE);
        spawned.add(entity.getUUID());
        if (entity instanceof Raider raider) {
            raider.setCanJoinRaid(false);
        }
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
            // A daytime siege used to resolve itself in a few seconds of burning zombies, because
            // "alive == 0" is what ends a wave. A helmet is the vanilla-idiomatic fix and reads
            // better on camera than a mob on fire.
            if ((mob instanceof Zombie || mob instanceof AbstractSkeleton)
                    && mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
            }
        }
    }

    /**
     * Points every wave mob at the action.
     *
     * <p>The anchor and the target are two different questions. A creative or spectator player is
     * never a valid target (vanilla's own targeting goals skip them too), but the mobs still have to
     * <em>walk somewhere</em> - otherwise a lone creative director gets a wave that stands in the
     * spawn ring for the full timeout and never appears in shot.
     */
    private void converge(EventContext ctx) {
        ServerLevel level = ctx.level();
        Vec3 anchor = ctx.focus();
        for (Entity entity : taggedMobs(ctx)) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            ServerPlayer nearest = nearestTarget(level, mob.position());
            if (nearest != null) {
                if (mob.getTarget() == null || !mob.getTarget().isAlive()) {
                    mob.setTarget(nearest);
                }
                if (mob.distanceToSqr(nearest) > 36.0D) {
                    mob.getNavigation().moveTo(nearest, 1.15D);
                }
            } else if (mob.distanceToSqr(anchor.x, anchor.y, anchor.z) > 36.0D) {
                mob.getNavigation().moveTo(anchor.x, anchor.y, anchor.z, 1.15D);
            }
        }
    }

    private ServerPlayer nearestTarget(ServerLevel level, Vec3 from) {
        ServerPlayer best = null;
        double bestSq = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative()) {
                continue;
            }
            double distSq = player.position().distanceToSqr(from);
            if (distSq < bestSq) {
                bestSq = distSq;
                best = player;
            }
        }
        return best;
    }

    /** The mobs this siege owns that are still resolvable and alive. No level-wide scan. */
    private List<Entity> taggedMobs(EventContext ctx) {
        List<Entity> out = new ArrayList<>();
        for (UUID id : spawned) {
            Entity entity = ctx.level().getEntity(id);
            if (entity != null && entity.isAlive()) {
                out.add(entity);
            }
        }
        return out;
    }

    private int countAlive(EventContext ctx) {
        int count = 0;
        for (UUID id : spawned) {
            Entity entity = ctx.level().getEntity(id);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                count++;
            }
        }
        return count;
    }

    /** One sweep, on resume only, to re-adopt the mobs the tag says belong to this siege. */
    private void recoverSpawnedFromTags(EventContext ctx) {
        for (Entity entity : ctx.level().getAllEntities()) {
            if (entity.isAlive() && entity.getTags().contains(EventHooks.TAG_SIEGE)) {
                spawned.add(entity.getUUID());
            }
        }
    }

    private void victory(EventContext ctx) {
        ServerLevel level = ctx.level();
        Vec3 where = ctx.focus();
        Component title = Component.translatable("title.creator_events.siege_repelled")
                .withStyle(ChatFormatting.GREEN);
        for (ServerPlayer player : ctx.players()) {
            Titles.show(player, title, Component.empty(), 5, 60, 20);
        }
        Fx.sound(level, where, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 1.0F);

        LootTable table = level.getServer().reloadableRegistries().getLootTable(SIEGE_REWARD);
        if (table == LootTable.EMPTY) {
            return;
        }
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, where)
                .create(LootContextParamSets.CHEST);
        for (ItemStack stack : table.getRandomItems(params, level.getRandom())) {
            if (stack.isEmpty()) {
                continue;
            }
            level.addFreshEntity(new ItemEntity(level, where.x, where.y + 0.5D, where.z, stack));
        }
    }
}
