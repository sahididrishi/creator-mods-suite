package dev.riftal.creator.features.events.net;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.events.EventsFeature;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The whole client-visible state of the director, pushed to every player on every change, once a
 * second while an event runs, and once to a player the moment they join.
 *
 * <p>Hand-written codec: {@code StreamCodec.composite} tops out at six fields in 1.21.1 and this
 * carries seventeen. Everything here is cosmetic - the client never sends anything back - so a
 * vanilla client that cannot read it simply misses the tint and the HUD line.
 *
 * @param eventId       registered event id, or {@code ""} when nothing is running
 * @param phaseId       current phase id, or {@code ""}
 * @param phaseIndex    0-based phase index
 * @param phaseTick     ticks elapsed in the current phase
 * @param phaseDuration ticks the phase lasts, or {@code -1} for an open phase
 * @param progress      0..1 for the HUD bar
 * @param tintR         sky tint red, 0..1
 * @param tintG         sky tint green, 0..1
 * @param tintB         sky tint blue, 0..1
 * @param tintStrength  0 = no tint
 * @param voidY         kill-plane height for {@code voidrise}, {@link Double#NaN} otherwise
 * @param wave          current siege wave, 1-based, or 0
 * @param waveTotal     total siege waves, or 0
 * @param alive         event-owned hostiles still alive, or 0
 * @param hud           false when the director hid the HUD line for a clean thumbnail
 * @param dimension     dimension the event is anchored to ({@code "minecraft:overworld"}), or
 *                      {@code ""} while idle; the client compares it against its own level so a
 *                      player in another dimension gets neither the tint nor the HUD line
 * @param ambientLoop   sound event id the client should loop while the event runs, or {@code ""}
 */
public record EventStatePayload(String eventId,
                                String phaseId,
                                int phaseIndex,
                                int phaseTick,
                                int phaseDuration,
                                float progress,
                                float tintR,
                                float tintG,
                                float tintB,
                                float tintStrength,
                                double voidY,
                                int wave,
                                int waveTotal,
                                int alive,
                                boolean hud,
                                String dimension,
                                String ambientLoop) implements CustomPacketPayload {

    /** Nothing is running: the client clears its tint, its loop and the HUD line. */
    public static final EventStatePayload IDLE = new EventStatePayload(
            "", "", 0, 0, -1, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, Double.NaN, 0, 0, 0, true, "", "");

    public static final Type<EventStatePayload> TYPE =
            Payloads.type(EventsFeature.NAMESPACE, "event_state");

    public static final StreamCodec<RegistryFriendlyByteBuf, EventStatePayload> CODEC =
            StreamCodec.of(EventStatePayload::write, EventStatePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, EventStatePayload payload) {
        buf.writeUtf(payload.eventId, 64);
        buf.writeUtf(payload.phaseId, 64);
        buf.writeVarInt(payload.phaseIndex);
        buf.writeVarInt(payload.phaseTick);
        buf.writeVarInt(payload.phaseDuration);
        buf.writeFloat(payload.progress);
        buf.writeFloat(payload.tintR);
        buf.writeFloat(payload.tintG);
        buf.writeFloat(payload.tintB);
        buf.writeFloat(payload.tintStrength);
        buf.writeDouble(payload.voidY);
        buf.writeVarInt(payload.wave);
        buf.writeVarInt(payload.waveTotal);
        buf.writeVarInt(payload.alive);
        buf.writeBoolean(payload.hud);
        buf.writeUtf(payload.dimension, 128);
        buf.writeUtf(payload.ambientLoop, 128);
    }

    private static EventStatePayload read(RegistryFriendlyByteBuf buf) {
        return new EventStatePayload(
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readDouble(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readUtf(128),
                buf.readUtf(128));
    }

    /** Copy with a different HUD flag - used for the idle broadcast. */
    public EventStatePayload withHud(boolean visible) {
        return new EventStatePayload(eventId, phaseId, phaseIndex, phaseTick, phaseDuration, progress,
                tintR, tintG, tintB, tintStrength, voidY, wave, waveTotal, alive, visible,
                dimension, ambientLoop);
    }

    /** True when an event is running. */
    public boolean active() {
        return !eventId.isEmpty();
    }

    /**
     * True when this state belongs to the dimension the viewer is standing in.
     *
     * <p>An idle payload carries no dimension and matches everywhere, so leaving an event's
     * dimension and coming back needs no extra packet. An unknown viewer dimension (no level yet)
     * matches nothing.
     */
    public boolean appliesTo(String viewerDimension) {
        if (!active() || dimension.isEmpty()) {
            return true;
        }
        return dimension.equals(viewerDimension);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
