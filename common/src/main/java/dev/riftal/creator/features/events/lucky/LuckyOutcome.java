package dev.riftal.creator.features.events.lucky;

/**
 * One entry of the lucky-rain drop table.
 *
 * <p>The shape is an original re-expression of the widely documented "weight plus luck" drop-table
 * idea; it is deliberately flat so a data pack author can read it without a schema.
 *
 * @param type       one of {@code items}, {@code entity}, {@code explosion}, {@code command},
 *                   {@code effect}
 * @param weight     relative pick weight before luck is applied; must be positive
 * @param luck       how much player luck favours this outcome; negative outcomes are the nasty ones
 * @param id         loot table id, entity id or effect id depending on {@code type}
 * @param command    the command to run for {@code type = command}
 * @param count      entity count, or loot rolls
 * @param radius     explosion radius, or effect radius in blocks
 * @param duration   effect duration in ticks
 * @param amplifier  effect amplifier
 * @param fire       whether an {@code explosion} outcome sets fires
 */
public record LuckyOutcome(String type,
                           int weight,
                           int luck,
                           String id,
                           String command,
                           int count,
                           double radius,
                           int duration,
                           int amplifier,
                           boolean fire) {

    /** Outcome kinds this feature knows how to execute. */
    public static final String TYPE_ITEMS = "items";
    public static final String TYPE_ENTITY = "entity";
    public static final String TYPE_EXPLOSION = "explosion";
    public static final String TYPE_COMMAND = "command";
    public static final String TYPE_EFFECT = "effect";

    /**
     * True when this feature can actually run the outcome.
     *
     * <p>The plan's example table also shows a {@code structure} entry; that executor is marked
     * stretch and is <em>not</em> implemented, so such an entry is dropped at parse time with a
     * {@code LOG.warn} rather than failing silently.
     */
    public boolean isKnownType() {
        return TYPE_ITEMS.equals(type)
                || TYPE_ENTITY.equals(type)
                || TYPE_EXPLOSION.equals(type)
                || TYPE_COMMAND.equals(type)
                || TYPE_EFFECT.equals(type);
    }
}
