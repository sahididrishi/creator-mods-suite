package dev.riftal.creator.features.rules.rules;

import dev.riftal.creator.features.rules.RuleIds;
import dev.riftal.creator.features.rules.RuleTags;
import dev.riftal.creator.features.rules.api.Rule;
import dev.riftal.creator.features.rules.api.RuleContext;
import dev.riftal.creator.features.rules.util.RuleAttributes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * <b>Minecraft but every mob is three times its size.</b>
 *
 * <p>Scale 3 and triple health. A mob that spawns while the rule is on is grown on the tick it
 * joins the level ({@code onEntityJoin}), so it is never seen at normal size; the 20-tick sweep
 * stays as the backstop for the one case that hook cannot see, a mob arriving from a chunk load. A
 * mob that already carries the scale modifier is skipped, so both paths are idempotent and the
 * sweep costs one attribute lookup per loaded mob per second.
 *
 * <p>Both modifiers are transient, so a mob in an unloaded chunk cannot be left permanently giant
 * by a rule that is switched off while it is away: it simply comes back its normal size. The
 * {@code creator_rules_giant} entity tag <em>is</em> persisted, and that is what tells the sweep
 * "this one has been giant before": re-applying the modifiers after a chunk cycle must not heal it,
 * or a giant zombie fought down to its last heart comes back full every time its chunk reloads.
 */
public final class GiantMobsRule implements Rule {

    /** Added to {@code generic.scale} (base 1.0), i.e. 3x. */
    public static final double SCALE_BONUS = 2.0D;

    /** {@code ADD_MULTIPLIED_TOTAL} on max health, i.e. 3x. */
    public static final double HEALTH_MULTIPLIER = 2.0D;

    @Override
    public String id() {
        return "giant_mobs";
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    @Override
    public void onEnable(RuleContext ctx) {
        tick(ctx);
    }

    @Override
    public void onEntityJoin(RuleContext ctx, ServerLevel level, Entity entity) {
        if (entity instanceof Mob mob && shouldGrow(mob)) {
            grow(mob);
        }
    }

    @Override
    public void tick(RuleContext ctx) {
        for (ServerLevel level : ctx.server().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Mob mob && shouldGrow(mob)) {
                    grow(mob);
                }
            }
        }
    }

    @Override
    public void onDisable(RuleContext ctx) {
        for (ServerLevel level : ctx.server().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Mob mob
                        && RuleAttributes.has(mob, Attributes.SCALE, RuleIds.GIANT_SCALE)) {
                    shrink(mob);
                }
            }
        }
    }

    private static boolean shouldGrow(Mob mob) {
        return !RuleAttributes.has(mob, Attributes.SCALE, RuleIds.GIANT_SCALE)
                && !mob.getType().is(RuleTags.NO_GIANT);
    }

    /**
     * Applies both modifiers and tags the mob. Public so the GameTest can drive it directly.
     *
     * <p>The top-up to full health happens on the <em>first</em> growth only. Every later call -
     * the sweep meeting the mob again after its chunk reloaded and dropped the transient modifiers
     * - just pulls the current health back inside the (larger) maximum, so damage sticks.
     */
    public static void grow(Mob mob) {
        boolean firstGrowth = !mob.getTags().contains(RuleIds.GIANT_TAG);
        RuleAttributes.apply(mob, Attributes.SCALE, RuleIds.GIANT_SCALE, SCALE_BONUS,
                AttributeModifier.Operation.ADD_VALUE);
        RuleAttributes.apply(mob, Attributes.MAX_HEALTH, RuleIds.GIANT_HEALTH, HEALTH_MULTIPLIER,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (firstGrowth) {
            RuleAttributes.heal(mob);
            mob.addTag(RuleIds.GIANT_TAG);
        } else {
            RuleAttributes.clampHealth(mob);
        }
    }

    /** Takes both modifiers back off and untags the mob. */
    public static void shrink(Mob mob) {
        RuleAttributes.remove(mob, Attributes.SCALE, RuleIds.GIANT_SCALE);
        RuleAttributes.remove(mob, Attributes.MAX_HEALTH, RuleIds.GIANT_HEALTH);
        RuleAttributes.clampHealth(mob);
        mob.removeTag(RuleIds.GIANT_TAG);
    }
}
