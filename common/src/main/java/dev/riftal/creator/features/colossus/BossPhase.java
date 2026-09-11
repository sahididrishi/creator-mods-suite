package dev.riftal.creator.features.colossus;

import net.minecraft.world.BossEvent;

/**
 * The Ashen Colossus' three fight phases, derived purely from its health fraction.
 *
 * <p>Pure logic: no level, no registries, unit-testable.
 *
 * <ul>
 *   <li>{@link #P1} - health above 66 %</li>
 *   <li>{@link #P2} - health above 33 %</li>
 *   <li>{@link #P3} - everything below</li>
 * </ul>
 */
public enum BossPhase {

    P1(1, BossEvent.BossBarColor.YELLOW),
    P2(2, BossEvent.BossBarColor.RED),
    P3(3, BossEvent.BossBarColor.PURPLE);

    /** Health fraction at or below which the fight enters {@link #P2}. */
    public static final float P2_THRESHOLD = 0.66F;

    /** Health fraction at or below which the fight enters {@link #P3}. */
    public static final float P3_THRESHOLD = 0.33F;

    private final int index;
    private final BossEvent.BossBarColor barColor;

    BossPhase(int index, BossEvent.BossBarColor barColor) {
        this.index = index;
        this.barColor = barColor;
    }

    /** 1, 2 or 3 - the number the commands and the synched entity data speak in. */
    public int index() {
        return index;
    }

    /** Boss bar colour for this phase: yellow, then red, then purple. */
    public BossEvent.BossBarColor barColor() {
        return barColor;
    }

    /** True once the arena ring, the enrage speed bonus and the darkened screen are live. */
    public boolean isEnraged() {
        return this == P3;
    }

    /** The phase a boss at this health fraction belongs in. Clamps outside 0..1. */
    public static BossPhase forHealthFraction(float healthFraction) {
        if (healthFraction > P2_THRESHOLD) {
            return P1;
        }
        if (healthFraction > P3_THRESHOLD) {
            return P2;
        }
        return P3;
    }

    /** Phase for a stored index. Anything out of range falls back to {@link #P1}. */
    public static BossPhase byIndex(int index) {
        return switch (index) {
            case 2 -> P2;
            case 3 -> P3;
            default -> P1;
        };
    }

    /**
     * Phases never run backwards during a fight: healing the boss with {@code /colossus hp 100}
     * does not undo an enrage. Use {@code forHealthFraction} directly when you really do want to
     * force a phase down (that is what {@code /colossus phase} does).
     */
    public static BossPhase next(BossPhase current, float healthFraction) {
        BossPhase fromHealth = forHealthFraction(healthFraction);
        return fromHealth.index >= current.index ? fromHealth : current;
    }
}
