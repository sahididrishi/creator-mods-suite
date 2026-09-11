package dev.riftal.creator.features.events.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The looping bed an event asks for through {@code EventStatePayload#ambientLoop()} - the blood
 * moon's drone, today.
 *
 * <p><b>Client only.</b> Reached from {@link EventSoundLoop}, which is reached only from the
 * client-side mixin; nothing on a dedicated server loads this class.
 *
 * <p>A loop rather than a repeated server one-shot on purpose: the old code re-sent a positional
 * sound once every five seconds <em>per player</em>, so several players standing together heard
 * several overlapping copies, and stopping the event left the last copy playing out. A tickable
 * instance is started once, fades in, follows the listener and is stopped the moment the payload
 * says so.
 */
public final class EventAmbientSound extends AbstractTickableSoundInstance {

    /** Ticks the loop takes to reach full volume, and to fade back out. */
    public static final int FADE_TICKS = 40;

    /** Full volume of the bed. Deliberately under the events' one-shots. */
    public static final float FULL_VOLUME = 0.6F;

    private float target = FULL_VOLUME;

    private EventAmbientSound(SoundEvent soundEvent) {
        super(soundEvent, SoundSource.AMBIENT, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = FULL_VOLUME / FADE_TICKS;
        this.pitch = 1.0F;
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;
    }

    /**
     * A loop for {@code soundId}, or null when the id is not a valid resource location.
     *
     * <p>The {@link SoundEvent} is built rather than looked up: {@code AbstractSoundInstance} only
     * ever reads {@code getLocation()} off it, and building one keeps this class away from the
     * registries entirely.
     */
    public static EventAmbientSound of(String soundId) {
        ResourceLocation id = ResourceLocation.tryParse(soundId);
        return id == null ? null : new EventAmbientSound(SoundEvent.createVariableRangeEvent(id));
    }

    /** The sound event id this loop is playing, for the "did the payload change?" comparison. */
    public String soundId() {
        return getLocation().toString();
    }

    /** Starts the fade out. The instance stops itself once it is silent. */
    public void fadeOut() {
        this.target = 0.0F;
    }

    /** True once {@link #fadeOut()} has been asked for. */
    public boolean isFading() {
        return this.target <= 0.0F;
    }

    /**
     * Starting volume is one fade step rather than zero: {@code SoundInstance#canStartSilent()} is
     * false by default and a zero-volume start is dropped by the sound engine.
     */
    @Override
    public void tick() {
        float step = FULL_VOLUME / FADE_TICKS;
        if (this.volume < this.target) {
            this.volume = Math.min(this.target, this.volume + step);
        } else if (this.volume > this.target) {
            this.volume = Math.max(this.target, this.volume - step);
            if (this.volume <= 0.0F) {
                stop();
            }
        }
    }
}
