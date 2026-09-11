package dev.riftal.creator.features.events.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * One directed world event. Implementations live in
 * {@code dev.riftal.creator.features.events.events} and are created fresh by
 * {@link EventRegistry} every time the director starts one.
 *
 * <p>Contract for implementers: {@link #onStop} must undo everything the event owns. The suite's
 * selling point is that a take can be reset in one command, so a half-cleaned event is a bug.
 */
public interface WorldEvent {

    /** Stable lowercase id; also the command argument and the {@code event.creator_events.<id>} key. */
    String id();

    /** The phases this event runs through, in order. Never empty. */
    List<EventPhase> phases();

    /** Display name, from the feature's lang file. */
    default Component displayName() {
        return Component.translatable("event.creator_events." + id());
    }

    /**
     * Called once, before phase 0 begins.
     *
     * @param resumed true when the event is being rebuilt after a server restart, in which case
     *                {@link #load} has already run and world changes are already in place
     */
    void onStart(EventContext ctx, boolean resumed);

    /** Called as each phase begins, including phase 0 and including after a skip. */
    default void onPhaseStart(EventContext ctx, EventPhase phase, int phaseIndex) {
    }

    /** Called every server tick while the event is active. {@code phaseTick} starts at 1. */
    default void tick(EventContext ctx, EventPhase phase, int phaseTick) {
    }

    /** Default: a phase with a duration ends when it runs out; an open phase never does. */
    default boolean isPhaseComplete(EventContext ctx, EventPhase phase, int phaseTick) {
        return phase.durationTicks() >= 0 && phaseTick >= phase.durationTicks();
    }

    /**
     * {@code /event timer <seconds>}. Return true if the event consumed it itself - {@code voidrise}
     * reads it as "reach the ceiling in this long". Returning false lets the manager override the
     * current phase duration instead, which is what every other event wants.
     */
    default boolean onTimer(EventContext ctx, int seconds) {
        return false;
    }

    /** Tear down. Must remove mobs, boss bars, scheduled tasks and tints this event created. */
    void onStop(EventContext ctx, StopReason reason);

    /** A player who joined mid-event. Used to hand out glow, boss bars and the current state. */
    default void onPlayerJoin(EventContext ctx, ServerPlayer player) {
    }

    /** Sky and fog tint the client should lerp toward while this event runs. */
    default SkyTint skyTint() {
        return SkyTint.NONE;
    }

    /** 0..1 for the HUD bar. */
    default float progress(EventContext ctx, EventPhase phase, int phaseTick) {
        if (phase.durationTicks() <= 0) {
            return 0.0F;
        }
        float value = (float) phaseTick / phase.durationTicks();
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }

    /** The kill-plane height for {@code voidrise}, or {@link Double#NaN} for everything else. */
    default double voidY() {
        return Double.NaN;
    }

    /** Current wave number, 1-based, or 0 when the event has no waves. */
    default int wave() {
        return 0;
    }

    /** Total waves, or 0. */
    default int waveTotal() {
        return 0;
    }

    /** Hostiles still alive that belong to this event, or 0. */
    default int alive() {
        return 0;
    }

    /** Persist per-event state for a resume. */
    default void save(CompoundTag tag) {
    }

    /** Restore what {@link #save} wrote. Called before {@code onStart(ctx, true)}. */
    default void load(CompoundTag tag) {
    }
}
