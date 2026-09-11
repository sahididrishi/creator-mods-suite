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
 * <p>Scale 3 and triple health, applied to every loaded mob and to everything that spawns
 * afterwards. The work is done by a sweep every 20 ticks rather than an entity-spawn hook, which
 * keeps the rule loader-neutral and covers chunk loads, spawners and eggs with the same three
 * lines. A mob that already carries the scale modifier is skipped, so the sweep is idempotent and
 * costs one attribute lookup per loaded mob per second.
 *
 * <p>Both modifiers are transient, so a mob in an unloaded chunk cannot be left permanently giant
 * by a rule that is switched off while it is away: it simply comes back its normal size. The
 * {@code creator_rules_giant} tag is a marker for {@code /kill @e[tag=...]} and for tests, not the
 * idempotency check.
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

    /** Applies both modifiers and tags the mob. Public so the GameTest can drive it directly. */
    public static void grow(Mob mob) {
        RuleAttributes.apply(mob, Attributes.SCALE, RuleIds.GIANT_SCALE, SCALE_BONUS,
                AttributeModifier.Operation.ADD_VALUE);
        RuleAttributes.apply(mob, Attributes.MAX_HEALTH, RuleIds.GIANT_HEALTH, HEALTH_MULTIPLIER,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        RuleAttributes.heal(mob);
        mob.addTag(RuleIds.GIANT_TAG);
    }

    /** Takes both modifiers back off and untags the mob. */
    public static void shrink(Mob mob) {
        RuleAttributes.remove(mob, Attributes.SCALE, RuleIds.GIANT_SCALE);
        RuleAttributes.remove(mob, Attributes.MAX_HEALTH, RuleIds.GIANT_HEALTH);
        RuleAttributes.clampHealth(mob);
        mob.removeTag(RuleIds.GIANT_TAG);
    }
}
