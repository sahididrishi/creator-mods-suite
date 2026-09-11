package dev.riftal.creator.features.rules.util;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Add/remove helpers around 1.21's id-keyed {@link AttributeModifier}s.
 *
 * <p><b>Every modifier this feature applies is transient</b>, i.e. never written to the entity's
 * NBT. That is a deliberate design decision: a rule engine that can be switched off at any moment
 * must not be able to leave a permanent mark on a player who happened to be offline, or on a mob in
 * a chunk that happened to be unloaded. The cost is that every effect has to be re-applied when the
 * entity comes back - which is what {@code onPlayerJoin}, {@code onPlayerRespawn} and
 * {@code giant_mobs}' 20-tick sweep are for.
 *
 * <p>Every method is null-safe about the attribute instance: not every entity type carries every
 * attribute, and a rule must never crash a mob that happens not to have one.
 */
public final class RuleAttributes {

    /** Adds or updates a session-only modifier. Not saved to NBT. */
    public static void apply(LivingEntity entity, Holder<Attribute> attribute,
                             ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
    }

    /** Removes a modifier by id. Returns true if something was removed. */
    public static boolean remove(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return false;
        }
        return instance.removeModifier(id);
    }

    public static boolean has(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance != null && instance.hasModifier(id);
    }

    /**
     * Pulls current health back inside the maximum after a max-health change, and never leaves an
     * entity on zero (which would kill it as a side effect of a rule toggle).
     */
    public static void clampHealth(LivingEntity entity) {
        float max = entity.getMaxHealth();
        if (max <= 0.0F) {
            return;
        }
        if (entity.getHealth() > max) {
            entity.setHealth(max);
        }
    }

    /** Restores an entity to full health - used when a max-health rule is switched off. */
    public static void heal(LivingEntity entity) {
        entity.setHealth(entity.getMaxHealth());
    }

    private RuleAttributes() {
    }
}
