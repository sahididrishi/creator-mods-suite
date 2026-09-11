package dev.riftal.creator.features.rules;

import net.minecraft.resources.ResourceLocation;

/**
 * Stable ids for everything this feature stamps onto the world: attribute modifiers (1.21 keys
 * modifiers by {@link ResourceLocation}, not UUID), entity tags and scheduler owners.
 *
 * <p>They are constants because {@code onDisable} has to be able to remove exactly what
 * {@code onEnable} added, including after a restart, and including on entities that were not
 * loaded at the time.
 */
public final class RuleIds {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(RulesFeature.NAMESPACE, path);
    }

    /** {@code gravity_x3}: multiplies {@code generic.gravity}. */
    public static final ResourceLocation GRAVITY = id("gravity_x3");

    /** {@code gravity_x3}: lowers {@code generic.safe_fall_distance} so short falls hurt. */
    public static final ResourceLocation SAFE_FALL = id("gravity_x3_fall");

    /** {@code one_heart}: -18 max health. */
    public static final ResourceLocation ONE_HEART = id("one_heart");

    /** {@code hearts_currency}: max-health cost of everything bought so far. */
    public static final ResourceLocation HEARTS_SPENT = id("hearts_spent");

    /** {@code giant_mobs}: +2 scale on a mob. */
    public static final ResourceLocation GIANT_SCALE = id("giant_scale");

    /** {@code giant_mobs}: x3 max health on a mob. */
    public static final ResourceLocation GIANT_HEALTH = id("giant_health");

    /** Entity tag marking a mob {@code giant_mobs} has grown, for {@code /kill} selectors and tests. */
    public static final String GIANT_TAG = "creator_rules_giant";

    /** {@link dev.riftal.creator.core.sched.TickScheduler} owner for delayed explosions. */
    public static final ResourceLocation SCHED_EXPLODE = id("blocks_explode");

    private RuleIds() {
    }
}
