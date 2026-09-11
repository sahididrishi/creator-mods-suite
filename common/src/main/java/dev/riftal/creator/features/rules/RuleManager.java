package dev.riftal.creator.features.rules;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.features.rules.api.Rule;
import dev.riftal.creator.features.rules.api.RuleContext;
import dev.riftal.creator.features.rules.api.RuleRegistry;
import dev.riftal.creator.features.rules.net.RuleToastPayload;
import dev.riftal.creator.features.rules.net.RulesSyncPayload;
import dev.riftal.creator.features.rules.preset.RulePreset;
import dev.riftal.creator.features.rules.preset.RulePresets;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static dev.riftal.creator.Constants.LOG;

/**
 * The engine. Owns which rules are on, fans every hook out to exactly those rules, persists the
 * set with the world and mirrors it to every client's HUD.
 *
 * <p>All state is per server session: {@link #start(MinecraftServer)} runs on the first server tick
 * of a world and restores the persisted set (calling {@code onEnable} for each, so a restarted
 * world is never "silently active without its hooks"), {@link #stop()} runs on shutdown.
 *
 * <p>Every public entry point is a no-op while no server is running, which is what makes the whole
 * feature inert when it is switched off in {@code config/creatormods.json}.
 */
public final class RuleManager {

    /** Outcome of a toggle, so the command layer can pick the right message. */
    public enum Result {
        /** The rule is now on. */
        ENABLED,
        /** The rule is now off. */
        DISABLED,
        /** The rule was already in the requested state; nothing happened. */
        ALREADY,
        /** No rule with that id is registered. */
        UNKNOWN,
        /** There is no running server to change anything on. */
        NO_SERVER
    }

    /** How often rule-private state (timers) is flushed into the SavedData, in ticks. */
    private static final int PERSIST_INTERVAL = 200;

    private static final LinkedHashSet<String> ACTIVE = new LinkedHashSet<>();

    private static MinecraftServer server;
    private static RuleContext context;
    private static RuleSavedData data;
    private static long ticks;

    // ---------------------------------------------------------------- lifecycle

    /** First server tick of a world: load the saved set and switch those rules on for real. */
    public static void start(MinecraftServer newServer) {
        stop();
        server = newServer;
        context = new RuleContext(newServer);

        ServerLevel overworld = newServer.overworld();
        if (overworld == null) {
            LOG.warn("[rules] no overworld yet; rule state will load on a later tick");
            server = null;
            context = null;
            return;
        }
        data = overworld.getDataStorage().computeIfAbsent(RuleSavedData.factory(), RuleSavedData.FILE_NAME);

        for (Rule rule : RuleRegistry.all()) {
            guard(rule, "load", () -> rule.load(data.ruleState(rule.id())));
        }
        for (String id : data.active()) {
            Rule rule = RuleRegistry.byId(id);
            if (rule == null) {
                LOG.warn("[rules] saved rule '{}' is not registered any more; dropping it", id);
                continue;
            }
            ACTIVE.add(id);
            guard(rule, "onEnable", () -> rule.onEnable(context));
        }
        data.setActive(ACTIVE);
        LOG.info("[rules] {} of {} rule(s) active in this world: {}",
                ACTIVE.size(), RuleRegistry.size(), ACTIVE);
    }

    /** Server shutting down. Flush timers, drop every reference, cancel our scheduled work. */
    public static void stop() {
        if (server != null && data != null) {
            persistRuleState();
        }
        ACTIVE.clear();
        TickScheduler.cancelAll(RuleIds.SCHED_EXPLODE);
        RulePresets.clear();
        server = null;
        context = null;
        data = null;
        ticks = 0L;
    }

    /** True once {@link #start} has run for the current server. */
    public static boolean isRunning() {
        return server != null && context != null && data != null;
    }

    /** The server the engine is bound to, or {@code null}. */
    public static MinecraftServer server() {
        return server;
    }

    // ---------------------------------------------------------------- ticking

    /** End of every server tick. Fans out to active rules that are due this tick. */
    public static void tick() {
        if (!isRunning()) {
            return;
        }
        ticks++;
        for (String id : List.copyOf(ACTIVE)) {
            Rule rule = RuleRegistry.byId(id);
            if (rule == null) {
                continue;
            }
            int interval = Math.max(1, rule.tickInterval());
            if (ticks % interval != 0L) {
                continue;
            }
            try {
                rule.tick(context);
            } catch (Throwable t) {
                LOG.error("[rules] rule '{}' threw while ticking; switching it off", id, t);
                set(id, false);
            }
        }
        if (ticks % PERSIST_INTERVAL == 0L) {
            persistRuleState();
        }
    }

    // ---------------------------------------------------------------- queries

    public static boolean isActive(String id) {
        return ACTIVE.contains(id);
    }

    /** Active rule ids, in the order they were switched on. */
    public static List<String> active() {
        return List.copyOf(ACTIVE);
    }

    public static boolean hudEnabled() {
        return data == null || data.hud();
    }

    /** Shows or hides the client-side rule list. Broadcast immediately. */
    public static void setHudEnabled(boolean value) {
        if (data == null) {
            return;
        }
        data.setHud(value);
        broadcast();
    }

    // ---------------------------------------------------------------- toggling

    /** Switches one rule on or off. */
    public static Result set(String id, boolean on) {
        Rule rule = RuleRegistry.byId(id);
        if (rule == null) {
            return Result.UNKNOWN;
        }
        if (!isRunning()) {
            return Result.NO_SERVER;
        }
        if (ACTIVE.contains(id) == on) {
            return Result.ALREADY;
        }
        applyToggle(rule, on);
        persistActive();
        broadcast();
        Payloads.sendToAll(server, new RuleToastPayload(id, on));
        LOG.info("[rules] {} {}", id, on ? "ON" : "OFF");
        return on ? Result.ENABLED : Result.DISABLED;
    }

    /** Flips one rule. */
    public static Result toggle(String id) {
        Rule rule = RuleRegistry.byId(id);
        if (rule == null) {
            return Result.UNKNOWN;
        }
        return set(id, !ACTIVE.contains(id));
    }

    /**
     * Applies a preset. {@code replace} presets switch off everything they do not name first.
     *
     * @return how many rules are active afterwards, or -1 if there is no running server
     */
    public static int applyPreset(RulePreset preset) {
        if (!isRunning()) {
            return -1;
        }
        boolean changed = false;
        if (preset.replace()) {
            for (String id : List.copyOf(ACTIVE)) {
                if (!preset.rules().contains(id)) {
                    Rule rule = RuleRegistry.byId(id);
                    if (rule != null) {
                        applyToggle(rule, false);
                        changed = true;
                    }
                }
            }
        }
        for (String id : preset.rules()) {
            Rule rule = RuleRegistry.byId(id);
            if (rule != null && !ACTIVE.contains(id)) {
                applyToggle(rule, true);
                changed = true;
            }
        }
        if (changed) {
            persistActive();
            broadcast();
        }
        LOG.info("[rules] preset '{}' applied; {} rule(s) active", preset.id(), ACTIVE.size());
        return ACTIVE.size();
    }

    /**
     * Switches everything off.
     *
     * @return how many rules were switched off, or -1 if there is no running server
     */
    public static int clearAll() {
        if (!isRunning()) {
            return -1;
        }
        int count = ACTIVE.size();
        for (String id : List.copyOf(ACTIVE)) {
            Rule rule = RuleRegistry.byId(id);
            if (rule != null) {
                applyToggle(rule, false);
            }
        }
        if (count > 0) {
            persistActive();
            broadcast();
            LOG.info("[rules] all {} rule(s) switched off", count);
        }
        return count;
    }

    private static void applyToggle(Rule rule, boolean on) {
        if (on) {
            ACTIVE.add(rule.id());
            guard(rule, "onEnable", () -> rule.onEnable(context));
        } else {
            ACTIVE.remove(rule.id());
            guard(rule, "onDisable", () -> rule.onDisable(context));
        }
    }

    // ---------------------------------------------------------------- hook fan-out

    /** A player finished joining. Re-apply active effects, strip stale ones, send the HUD state. */
    public static void onPlayerJoin(ServerPlayer player) {
        if (!isRunning()) {
            return;
        }
        for (Rule rule : RuleRegistry.all()) {
            if (ACTIVE.contains(rule.id())) {
                guard(rule, "onPlayerJoin", () -> rule.onPlayerJoin(context, player));
            } else {
                guard(rule, "stripFrom", () -> rule.stripFrom(player));
            }
        }
        syncTo(player);
    }

    /** A player respawned; this is a new entity, so per-player effects must be re-applied. */
    public static void onPlayerRespawn(ServerPlayer player) {
        if (!isRunning()) {
            return;
        }
        for (Rule rule : RuleRegistry.all()) {
            if (ACTIVE.contains(rule.id())) {
                guard(rule, "onPlayerRespawn", () -> rule.onPlayerRespawn(context, player));
            } else {
                guard(rule, "stripFrom", () -> rule.stripFrom(player));
            }
        }
        syncTo(player);
    }

    /** A survival player broke a block; it is already gone from the world. */
    public static void onBlockBroken(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!isRunning()) {
            return;
        }
        for (String id : List.copyOf(ACTIVE)) {
            Rule rule = RuleRegistry.byId(id);
            if (rule != null) {
                guard(rule, "onBlockBroken", () -> rule.onBlockBroken(context, player, level, pos, state));
            }
        }
    }

    /** A player took a stack out of a crafting result slot, server side. */
    public static void onCraftTaken(ServerPlayer player, ItemStack result) {
        if (!isRunning()) {
            return;
        }
        for (String id : List.copyOf(ACTIVE)) {
            Rule rule = RuleRegistry.byId(id);
            if (rule != null) {
                guard(rule, "onCraftTaken", () -> rule.onCraftTaken(context, player, result));
            }
        }
    }

    /**
     * Last active rule to claim the block's drops wins.
     *
     * @return the replacement list, or {@code null} to keep vanilla's
     */
    public static List<ItemStack> remapBlockDrops(ServerLevel level, BlockPos pos, BlockState state,
                                                  List<ItemStack> original) {
        if (!isRunning()) {
            return null;
        }
        List<ItemStack> result = null;
        for (String id : List.copyOf(ACTIVE)) {
            Rule rule = RuleRegistry.byId(id);
            if (rule == null) {
                continue;
            }
            try {
                List<ItemStack> replacement = rule.remapBlockDrops(context, level, pos, state,
                        result == null ? original : result);
                if (replacement != null) {
                    result = replacement;
                }
            } catch (Throwable t) {
                LOG.error("[rules] rule '{}' threw while remapping block drops", id, t);
            }
        }
        return result;
    }

    /**
     * @return the replacement drop list for a dying mob, or {@code null} to keep vanilla's
     */
    public static List<ItemStack> remapMobDrops(LivingEntity entity) {
        if (!isRunning()) {
            return null;
        }
        List<ItemStack> result = null;
        for (String id : List.copyOf(ACTIVE)) {
            Rule rule = RuleRegistry.byId(id);
            if (rule == null) {
                continue;
            }
            try {
                List<ItemStack> replacement = rule.remapMobDrops(context, entity);
                if (replacement != null) {
                    result = replacement;
                }
            } catch (Throwable t) {
                LOG.error("[rules] rule '{}' threw while remapping mob drops", id, t);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- sync + persistence

    /** Pushes the active list and HUD flag to every connected client. */
    public static void broadcast() {
        if (server == null) {
            return;
        }
        Payloads.sendToAll(server, new RulesSyncPayload(new ArrayList<>(ACTIVE), hudEnabled()));
    }

    /** Pushes the active list to one client - used on join. */
    public static void syncTo(ServerPlayer player) {
        Payloads.sendToPlayer(player, new RulesSyncPayload(new ArrayList<>(ACTIVE), hudEnabled()));
    }

    private static void persistActive() {
        if (data != null) {
            data.setActive(ACTIVE);
        }
        persistRuleState();
    }

    private static void persistRuleState() {
        if (data == null) {
            return;
        }
        for (Rule rule : RuleRegistry.all()) {
            CompoundTag tag = new CompoundTag();
            guard(rule, "save", () -> rule.save(tag));
            if (!tag.isEmpty()) {
                data.putRuleState(rule.id(), tag);
            }
        }
    }

    private static void guard(Rule rule, String phase, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            LOG.error("[rules] rule '{}' threw during {}", rule.id(), phase, t);
        }
    }

    private RuleManager() {
    }
}
