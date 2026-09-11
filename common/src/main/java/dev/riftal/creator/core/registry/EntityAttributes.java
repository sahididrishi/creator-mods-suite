package dev.riftal.creator.core.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Default attributes for custom living entities, declared loader-neutrally.
 *
 * <pre>{@code
 * EntityAttributes.register(COLOSSUS, () -> Monster.createMonsterAttributes()
 *         .add(Attributes.MAX_HEALTH, 300.0D)
 *         .add(Attributes.MOVEMENT_SPEED, 0.25D));
 * }</pre>
 *
 * <p>Call from {@code registerContent()}. The loader flushes into Fabric's
 * {@code FabricDefaultAttributeRegistry} / NeoForge's {@code EntityAttributeCreationEvent}.
 */
public final class EntityAttributes {

    private static final List<Entry> PENDING = new ArrayList<>();

    /** One declared attribute set. Loader glue only. */
    public record Entry(Supplier<? extends EntityType<? extends LivingEntity>> type,
                        Supplier<AttributeSupplier.Builder> builder) {
    }

    public static void register(Supplier<? extends EntityType<? extends LivingEntity>> type,
                                Supplier<AttributeSupplier.Builder> builder) {
        PENDING.add(new Entry(type, builder));
    }

    /** Loader glue only. */
    public static List<Entry> pending() {
        return List.copyOf(PENDING);
    }

    private EntityAttributes() {
    }
}
