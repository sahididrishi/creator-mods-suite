package dev.riftal.creator.features.events.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;

/**
 * Keeps at most one {@link EventAmbientSound} alive and in sync with the director's state.
 *
 * <p><b>Client only.</b> Driven once per client tick from {@code EventsMinecraftMixin}.
 */
public final class EventSoundLoop {

    private static EventAmbientSound playing;

    /**
     * Starts, swaps or fades the loop to match {@link ClientEventState#ambientLoop()}.
     *
     * @param minecraft the client; never null on the tick path
     */
    public static void tick(Minecraft minecraft) {
        String wanted = ClientEventState.ambientLoop();
        if (playing != null && playing.isStopped()) {
            playing = null;
        }
        if (playing != null && !playing.soundId().equals(wanted)) {
            playing.fadeOut();
            if (wanted.isEmpty()) {
                return;
            }
            playing = null;
        }
        if (wanted.isEmpty()) {
            return;
        }
        if (playing != null) {
            return;
        }
        EventAmbientSound loop = EventAmbientSound.of(wanted);
        if (loop == null) {
            return;
        }
        SoundManager sounds = minecraft.getSoundManager();
        sounds.play(loop);
        playing = loop;
    }

    /** Drops the loop without waiting for a fade. Called when the player leaves a world. */
    public static void stop(Minecraft minecraft) {
        if (playing == null) {
            return;
        }
        minecraft.getSoundManager().stop(playing);
        playing = null;
    }

    private EventSoundLoop() {
    }
}
