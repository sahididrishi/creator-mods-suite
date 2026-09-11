package dev.riftal.creator.features.events;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.core.sched.ScheduledTask;
import dev.riftal.creator.core.sched.TickScheduler;
import dev.riftal.creator.features.events.api.EventContext;
import dev.riftal.creator.features.events.api.EventPhase;
import dev.riftal.creator.features.events.api.EventRegistry;
import dev.riftal.creator.features.events.api.SkyTint;
import dev.riftal.creator.features.events.api.StopReason;
import dev.riftal.creator.features.events.api.WorldEvent;
import dev.riftal.creator.features.events.net.EventStatePayload;
import dev.riftal.creator.features.events.util.EventOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * The director itself: at most one live {@link WorldEvent}, its phase machine, its broadcast to the
 * client and its persistence.
 *
 * <p>Driven by a single repeating {@link TickScheduler} task rather than a per-tick scan, and armed
 * from the command registrar, which both loaders replay every time a server builds its dispatcher.
 * That is also where a fresh server is detected and an interrupted event is resumed from disk.
 */
public final class EventManager {

    /** How often the full state is pushed to clients while an event runs. */
    private static final int BROADCAST_PERIOD = 20;

    private static final String KEY_ID = "id";
    private static final String KEY_DIMENSION = "dimension";
    private static final String KEY_ORIGIN_X = "origin_x";
    private static final String KEY_ORIGIN_Y = "origin_y";
    private static final String KEY_ORIGIN_Z = "origin_z";
    private static final String KEY_STARTER = "starter";
    private static final String KEY_OPTIONS = "options";
    private static final String KEY_PHASE_INDEX = "phase_index";
    private static final String KEY_PHASE_TICK = "phase_tick";
    private static final String KEY_OVERRIDE = "duration_override";
    private static final String KEY_STATE = "state";

    private static MinecraftServer boundServer;
    private static ScheduledTask driver;
    private static EventSavedData savedData;

    private static WorldEvent active;
    private static EventContext context;
    private static int phaseIndex;
    private static int phaseTick;
    private static int durationOverride = -1;
    private static int sinceBroadcast;
    private static boolean hudVisible = true;

    /**
     * Arms the per-tick driver. Called from the command registrar, which both loaders replay on
     * server start and on every {@code /reload} - the one hook a feature gets before the first tick.
     *
     * <p>Cancels any existing driver first rather than testing the old handle: {@code
     * TickScheduler.clear()} on server stop drops tasks without marking them done, so a stale
     * handle would still claim to be alive on the next world load.
     */
    public static void ensureDriver() {
        TickScheduler.cancelAll(tag("director"));
        driver = TickScheduler.runRepeating(1, -1, EventManager::tick).tag(tag("director"));
    }

    /** A {@code creator_events:<path>} id, for scheduler tags. */
    public static ResourceLocation tag(String path) {
        return ResourceLocation.fromNamespaceAndPath(EventsFeature.NAMESPACE, path);
    }

    /** The running event, or null. */
    public static WorldEvent active() {
        return active;
    }

    /** The running event's context, or null. */
    public static EventContext context() {
        return context;
    }

    /** Id of the running event, or {@code ""}. */
    public static String activeId() {
        return active == null ? "" : active.id();
    }

    public static int phaseIndex() {
        return phaseIndex;
    }

    public static int phaseTick() {
        return phaseTick;
    }

    /** The current phase, or null when nothing is running. */
    public static EventPhase currentPhase() {
        if (active == null) {
            return null;
        }
        List<EventPhase> phases = active.phases();
        return phases.get(Math.min(phaseIndex, phases.size() - 1));
    }

    /** Duration of the current phase after any {@code /event timer} override; -1 when open. */
    public static int currentPhaseDuration() {
        EventPhase phase = currentPhase();
        if (phase == null) {
            return -1;
        }
        return durationOverride >= 0 ? durationOverride : phase.durationTicks();
    }

    /**
     * Starts {@code id}, replacing whatever was running.
     *
     * @return false when the id is not registered
     */
    public static boolean start(String id, MinecraftServer server, ServerLevel level, Vec3 origin,
                                UUID starterId, EventOptions options) {
        WorldEvent event = EventRegistry.create(id);
        if (event == null) {
            return false;
        }
        if (active != null) {
            stop(StopReason.STOPPED);
        }
        boundServer = server;
        context = new EventContext(server, level, origin, starterId, options);
        active = event;
        phaseIndex = 0;
        phaseTick = 0;
        durationOverride = -1;
        sinceBroadcast = 0;

        LOG.info("[events] start '{}' in {} at {} {}", id, level.dimension().location(),
                origin.x, origin.z);
        try {
            event.onStart(context, false);
            if (active == event) {
                event.onPhaseStart(context, currentPhase(), 0);
            }
        } catch (RuntimeException e) {
            LOG.error("[events] '{}' threw while starting; stopping it", id, e);
            stop(StopReason.STOPPED);
            return true;
        }
        persist();
        broadcast();
        return true;
    }

    /** Tears the running event down. No-op when nothing is running. */
    public static boolean stop(StopReason reason) {
        WorldEvent stopping = active;
        if (stopping == null) {
            return false;
        }
        EventContext ctx = context;
        active = null;
        context = null;
        try {
            stopping.onStop(ctx, reason);
        } catch (RuntimeException e) {
            LOG.error("[events] '{}' threw while stopping", stopping.id(), e);
        }
        TickScheduler.cancelAll(tag(stopping.id()));
        phaseIndex = 0;
        phaseTick = 0;
        durationOverride = -1;
        if (savedData != null) {
            savedData.setActive(null);
            savedData.setLastEventId(stopping.id());
        }
        LOG.info("[events] stop '{}' ({})", stopping.id(), reason);
        if (boundServer != null) {
            Payloads.sendToAll(boundServer, EventStatePayload.IDLE.withHud(hudVisible));
        }
        return true;
    }

    /**
     * Ends the current phase now. Past the last phase this finishes the event.
     *
     * @return the id of the phase that is now running, or {@code ""} when the event ended
     */
    public static String skip() {
        if (active == null) {
            return "";
        }
        if (phaseIndex + 1 >= active.phases().size()) {
            stop(StopReason.SKIPPED);
            return "";
        }
        advancePhase();
        EventPhase phase = currentPhase();
        return phase == null ? "" : phase.id();
    }

    /**
     * {@code /event timer}. The event gets first refusal; otherwise the current phase is retimed.
     *
     * @return false when nothing is running
     */
    public static boolean timer(int seconds) {
        if (active == null) {
            return false;
        }
        int clamped = Math.max(1, Math.min(seconds, 86400));
        boolean handled = false;
        try {
            handled = active.onTimer(context, clamped);
        } catch (RuntimeException e) {
            LOG.error("[events] '{}' threw handling a timer", active.id(), e);
        }
        if (!handled) {
            durationOverride = clamped * 20;
            phaseTick = Math.min(phaseTick, durationOverride - 1);
        }
        persist();
        broadcast();
        return true;
    }

    /** Called once per server tick by the scheduler driver. */
    private static void tick() {
        MinecraftServer server = TickScheduler.server();
        if (server == null) {
            return;
        }
        if (server != boundServer) {
            // A different world: whatever was in memory belonged to the previous session. There is
            // no server-stopping hook available to a feature, so release it here instead - that is
            // what frees a leaked boss bar and puts the monster spawn cap back.
            WorldEvent previous = active;
            EventContext previousContext = context;
            active = null;
            context = null;
            if (previous != null) {
                try {
                    previous.onStop(previousContext, StopReason.SERVER_STOP);
                } catch (RuntimeException e) {
                    LOG.error("[events] '{}' threw releasing the previous session", previous.id(), e);
                }
            }
            boundServer = server;
            savedData = null;
            EventsFeature.clearDataCaches();
            phaseIndex = 0;
            phaseTick = 0;
            durationOverride = -1;
            resumeFromDisk(server);
        }
        if (active == null) {
            return;
        }

        EventPhase phase = currentPhase();
        phaseTick++;
        WorldEvent running = active;
        try {
            running.tick(context, phase, phaseTick);
        } catch (RuntimeException e) {
            LOG.error("[events] '{}' threw during tick; stopping it", running.id(), e);
            stop(StopReason.STOPPED);
            return;
        }
        if (active != running) {
            return;
        }

        if (isPhaseComplete(phase)) {
            if (phaseIndex + 1 >= running.phases().size()) {
                stop(StopReason.FINISHED);
                return;
            }
            advancePhase();
            return;
        }

        if (++sinceBroadcast >= BROADCAST_PERIOD) {
            broadcast();
            persist();
        }
    }

    private static boolean isPhaseComplete(EventPhase phase) {
        if (durationOverride >= 0) {
            return phaseTick >= durationOverride;
        }
        try {
            return active.isPhaseComplete(context, phase, phaseTick);
        } catch (RuntimeException e) {
            LOG.error("[events] '{}' threw checking phase completion", active.id(), e);
            return true;
        }
    }

    private static void advancePhase() {
        phaseIndex++;
        phaseTick = 0;
        durationOverride = -1;
        WorldEvent running = active;
        try {
            running.onPhaseStart(context, currentPhase(), phaseIndex);
        } catch (RuntimeException e) {
            LOG.error("[events] '{}' threw entering phase {}", running.id(), phaseIndex, e);
        }
        if (active == running) {
            persist();
            broadcast();
        }
    }

    /** Pushes the whole state to every player. */
    public static void broadcast() {
        sinceBroadcast = 0;
        if (boundServer == null) {
            return;
        }
        Payloads.sendToAll(boundServer, snapshot());
    }

    /** The payload that describes the current state - also what the HUD renders from. */
    public static EventStatePayload snapshot() {
        if (active == null || context == null) {
            return EventStatePayload.IDLE;
        }
        EventPhase phase = currentPhase();
        SkyTint tint = active.skyTint();
        float progress;
        try {
            progress = active.progress(context, phase, phaseTick);
        } catch (RuntimeException e) {
            progress = 0.0F;
        }
        return new EventStatePayload(
                active.id(),
                phase == null ? "" : phase.id(),
                phaseIndex,
                phaseTick,
                currentPhaseDuration(),
                progress,
                tint.red(), tint.green(), tint.blue(), tint.strength(),
                active.voidY(),
                active.wave(),
                active.waveTotal(),
                active.alive(),
                hudVisible);
    }

    /** Whether the client should draw the director's HUD line. Toggled by {@code /event hud}. */
    public static boolean hudVisible() {
        return hudVisible;
    }

    /** {@code /event hud <on|off>}. The siege boss bar is vanilla and stays either way. */
    public static void setHudVisible(boolean visible) {
        hudVisible = visible;
        broadcast();
    }

    /** Id of the last event that finished, for {@code /event status} while idle. */
    public static String lastEventId() {
        return savedData == null ? "" : savedData.lastEventId();
    }

    private static EventSavedData savedData(MinecraftServer server) {
        if (savedData != null) {
            return savedData;
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return null;
        }
        savedData = overworld.getDataStorage()
                .computeIfAbsent(EventSavedData.factory(), EventSavedData.FILE_NAME);
        return savedData;
    }

    /** Writes the running event to the world's saved data so a restart can pick it back up. */
    public static void persist() {
        if (boundServer == null) {
            return;
        }
        EventSavedData data = savedData(boundServer);
        if (data == null) {
            return;
        }
        if (active == null || context == null) {
            data.setActive(null);
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_ID, active.id());
        tag.putString(KEY_DIMENSION, context.level().dimension().location().toString());
        tag.putDouble(KEY_ORIGIN_X, context.origin().x);
        tag.putDouble(KEY_ORIGIN_Y, context.origin().y);
        tag.putDouble(KEY_ORIGIN_Z, context.origin().z);
        if (context.starterId() != null) {
            tag.putUUID(KEY_STARTER, context.starterId());
        }
        tag.putString(KEY_OPTIONS, context.options().raw());
        tag.putInt(KEY_PHASE_INDEX, phaseIndex);
        tag.putInt(KEY_PHASE_TICK, phaseTick);
        tag.putInt(KEY_OVERRIDE, durationOverride);
        CompoundTag state = new CompoundTag();
        try {
            active.save(state);
        } catch (RuntimeException e) {
            LOG.error("[events] '{}' threw while saving", active.id(), e);
        }
        tag.put(KEY_STATE, state);
        data.setActive(tag);
    }

    private static void resumeFromDisk(MinecraftServer server) {
        EventSavedData data = savedData(server);
        if (data == null) {
            return;
        }
        CompoundTag tag = data.active();
        if (tag == null) {
            return;
        }
        String id = tag.getString(KEY_ID);
        WorldEvent event = EventRegistry.create(id);
        if (event == null) {
            LOG.warn("[events] saved event '{}' is not registered; dropping it", id);
            data.setActive(null);
            return;
        }
        ServerLevel level = resolveLevel(server, tag.getString(KEY_DIMENSION));
        if (level == null) {
            data.setActive(null);
            return;
        }
        Vec3 origin = new Vec3(tag.getDouble(KEY_ORIGIN_X), tag.getDouble(KEY_ORIGIN_Y),
                tag.getDouble(KEY_ORIGIN_Z));
        UUID starter = tag.hasUUID(KEY_STARTER) ? tag.getUUID(KEY_STARTER) : null;
        EventContext ctx = new EventContext(server, level, origin, starter,
                EventOptions.parse(tag.getString(KEY_OPTIONS)));

        context = ctx;
        active = event;
        phaseIndex = Math.max(0, Math.min(tag.getInt(KEY_PHASE_INDEX), event.phases().size() - 1));
        phaseTick = Math.max(0, tag.getInt(KEY_PHASE_TICK));
        durationOverride = tag.contains(KEY_OVERRIDE) ? tag.getInt(KEY_OVERRIDE) : -1;
        sinceBroadcast = 0;
        try {
            event.load(tag.getCompound(KEY_STATE));
            event.onStart(ctx, true);
        } catch (RuntimeException e) {
            LOG.error("[events] '{}' threw while resuming; dropping it", id, e);
            active = null;
            context = null;
            data.setActive(null);
            return;
        }
        LOG.info("[events] resumed '{}' at phase {} tick {}", id, phaseIndex, phaseTick);
        broadcast();
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String dimensionId) {
        ResourceLocation id = ResourceLocation.tryParse(dimensionId);
        if (id != null) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            if (level != null) {
                return level;
            }
        }
        return server.getLevel(Level.OVERWORLD);
    }

    private EventManager() {
    }
}
