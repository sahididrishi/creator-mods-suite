package dev.riftal.creator.features.events.api;

/**
 * A colour the client lerps the sky and fog toward while an event is running.
 *
 * @param red      0..1
 * @param green    0..1
 * @param blue     0..1
 * @param strength 0 = no tint at all, 1 = fully replace the vanilla colour
 */
public record SkyTint(float red, float green, float blue, float strength) {

    /** No tint. The client leaves vanilla colours alone. */
    public static final SkyTint NONE = new SkyTint(0.0F, 0.0F, 0.0F, 0.0F);

    /** The blood moon red. */
    public static final SkyTint BLOOD = new SkyTint(0.60F, 0.00F, 0.00F, 0.85F);

    /** Clamped copy with a new strength - used for the ramp in and the fade out. */
    public SkyTint withStrength(float newStrength) {
        return new SkyTint(red, green, blue, clamp(newStrength));
    }

    /** True when this tint would visibly change anything. */
    public boolean isActive() {
        return strength > 0.001F;
    }

    /**
     * Mixes {@code vanilla} toward this tint.
     *
     * @param channel 0 = red, 1 = green, 2 = blue
     */
    public float mix(int channel, float vanilla) {
        float target = switch (channel) {
            case 0 -> red;
            case 1 -> green;
            default -> blue;
        };
        float t = clamp(strength);
        return vanilla + (target - vanilla) * t;
    }

    private static float clamp(float value) {
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }
}
