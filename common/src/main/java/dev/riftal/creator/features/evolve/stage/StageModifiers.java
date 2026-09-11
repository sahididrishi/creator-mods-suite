package dev.riftal.creator.features.evolve.stage;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Turns an {@link EvolutionStage} into vanilla attribute modifiers, and takes them away again.
 *
 * <p>1.21 modifiers are keyed by a namespaced id rather than a UUID, so every modifier this class
 * writes uses one of the fixed ids below. {@link #apply} removes the old modifier by id before
 * adding the new one, which makes it idempotent: calling it twice leaves exactly one modifier per
 * attribute.
 *
 * <p>The stage modifiers are <em>permanent</em> ({@code addPermanentModifier}) so they serialise
 * into the player's vanilla attribute NBT and the player never loads at the wrong size for a frame.
 * The transformation movement lock is <em>transient</em> - it must never survive a crash.
 */
public final class StageModifiers {

    private static final String NAMESPACE = "creator_evolve";

    public static final ResourceLocation SCALE_ID = id("stage_scale");
    public static final ResourceLocation HEALTH_ID = id("stage_health");
    public static final ResourceLocation SPEED_ID = id("stage_speed");
    public static final ResourceLocation ATTACK_ID = id("stage_attack");
    public static final ResourceLocation STEP_ID = id("stage_step");
    public static final ResourceLocation JUMP_ID = id("stage_jump");
    public static final ResourceLocation BLOCK_REACH_ID = id("stage_reach_block");
    public static final ResourceLocation ENTITY_REACH_ID = id("stage_reach_entity");
    public static final ResourceLocation SAFE_FALL_ID = id("stage_safe_fall");
    public static final ResourceLocation TRANSFORM_LOCK_ID = id("transform_lock");

    /** Every id this class can write, including the transformation lock. Stable order. */
    public static final List<ResourceLocation> ALL_IDS = List.of(
            SCALE_ID, HEALTH_ID, SPEED_ID, ATTACK_ID, STEP_ID, JUMP_ID,
            BLOCK_REACH_ID, ENTITY_REACH_ID, SAFE_FALL_ID, TRANSFORM_LOCK_ID);

    /** The nine stage ids, without the transformation lock. */
    public static final List<ResourceLocation> STAGE_IDS = List.of(
            SCALE_ID, HEALTH_ID, SPEED_ID, ATTACK_ID, STEP_ID, JUMP_ID,
            BLOCK_REACH_ID, ENTITY_REACH_ID, SAFE_FALL_ID);

    /**
     * Writes every modifier for {@code stage} onto {@code entity}, replacing any it already had,
     * then refreshes the hitbox and clamps health into the new maximum.
     */
    public static void apply(LivingEntity entity, EvolutionStage stage) {
        set(entity, Attributes.SCALE, SCALE_ID, stage.scaleBonus(),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(entity, Attributes.MAX_HEALTH, HEALTH_ID, stage.healthBonus(),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(entity, Attributes.MOVEMENT_SPEED, SPEED_ID, stage.speedBonus(),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(entity, Attributes.ATTACK_DAMAGE, ATTACK_ID, stage.attackBonus(),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(entity, Attributes.STEP_HEIGHT, STEP_ID, stage.stepBonus(),
                AttributeModifier.Operation.ADD_VALUE);
        set(entity, Attributes.JUMP_STRENGTH, JUMP_ID, stage.jumpBonus(),
                AttributeModifier.Operation.ADD_VALUE);
        set(entity, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, stage.blockReachBonus(),
                AttributeModifier.Operation.ADD_VALUE);
        set(entity, Attributes.ENTITY_INTERACTION_RANGE, ENTITY_REACH_ID, stage.entityReachBonus(),
                AttributeModifier.Operation.ADD_VALUE);
        set(entity, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID, stage.safeFallBonus(),
                AttributeModifier.Operation.ADD_VALUE);

        entity.refreshDimensions();
        clampHealth(entity);
    }

    /** Removes every stage modifier (but not the transformation lock) and refreshes the hitbox. */
    public static void clear(LivingEntity entity) {
        for (ResourceLocation modifierId : STAGE_IDS) {
            removeEverywhere(entity, modifierId);
        }
        entity.refreshDimensions();
        clampHealth(entity);
    }

    /** Pins movement speed at zero for the duration of a transformation. Transient on purpose. */
    public static void lockMovement(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance == null) {
            return;
        }
        instance.addOrUpdateTransientModifier(new AttributeModifier(TRANSFORM_LOCK_ID, -1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** Releases {@link #lockMovement}. Safe to call when no lock is present. */
    public static void unlockMovement(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance != null) {
            instance.removeModifier(TRANSFORM_LOCK_ID);
        }
    }

    /** True while the transformation movement lock is on this entity. */
    public static boolean isMovementLocked(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        return instance != null && instance.hasModifier(TRANSFORM_LOCK_ID);
    }

    /** The amount currently written under {@code modifierId}, or 0 when there is no such modifier. */
    public static double modifierAmount(LivingEntity entity, Holder<Attribute> attribute,
                                        ResourceLocation modifierId) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return 0.0D;
        }
        AttributeModifier modifier = instance.getModifier(modifierId);
        return modifier == null ? 0.0D : modifier.amount();
    }

    private static void set(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation modifierId,
                            double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            // Not every living entity owns every attribute - the two interaction ranges are
            // player-only. Nothing to do, and nothing to complain about.
            return;
        }
        instance.removeModifier(modifierId);
        if (amount != 0.0D) {
            instance.addPermanentModifier(new AttributeModifier(modifierId, amount, operation));
        }
    }

    private static void removeEverywhere(LivingEntity entity, ResourceLocation modifierId) {
        for (Holder<Attribute> attribute : touchedAttributes()) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(modifierId);
            }
        }
    }

    /**
     * The attributes this class ever writes to. Built lazily rather than in a static initialiser so
     * that loading {@code StageModifiers} never forces {@code Attributes} class-init at an awkward
     * moment.
     */
    private static List<Holder<Attribute>> touchedAttributes() {
        return List.of(
                Attributes.SCALE, Attributes.MAX_HEALTH, Attributes.MOVEMENT_SPEED,
                Attributes.ATTACK_DAMAGE, Attributes.STEP_HEIGHT, Attributes.JUMP_STRENGTH,
                Attributes.BLOCK_INTERACTION_RANGE, Attributes.ENTITY_INTERACTION_RANGE,
                Attributes.SAFE_FALL_DISTANCE);
    }

    private static void clampHealth(LivingEntity entity) {
        float max = entity.getMaxHealth();
        if (entity.getHealth() > max) {
            entity.setHealth(max);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    private StageModifiers() {
    }
}
