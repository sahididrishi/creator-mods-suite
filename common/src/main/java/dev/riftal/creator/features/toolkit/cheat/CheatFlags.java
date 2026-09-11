package dev.riftal.creator.features.toolkit.cheat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The two sticky creator cheats, stored per player.
 *
 * <p>Immutable on purpose: attachment values are hashed and cached, so a stored value that is
 * mutated in place is the classic "my data doesn't save" bug.
 *
 * @param god invulnerable
 * @param fly creative-style flight allowed
 */
public record CheatFlags(boolean god, boolean fly) {

    /** Nothing on - the value a player starts with. */
    public static final CheatFlags NONE = new CheatFlags(false, false);

    public static final Codec<CheatFlags> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("god", false).forGetter(CheatFlags::god),
            Codec.BOOL.optionalFieldOf("fly", false).forGetter(CheatFlags::fly)
    ).apply(instance, CheatFlags::new));

    /** Copy with {@code god} changed. */
    public CheatFlags withGod(boolean value) {
        return new CheatFlags(value, fly);
    }

    /** Copy with {@code fly} changed. */
    public CheatFlags withFly(boolean value) {
        return new CheatFlags(god, value);
    }

    /** True when at least one cheat is on, i.e. this player needs re-applying after a respawn. */
    public boolean any() {
        return god || fly;
    }
}
