package dev.riftal.creator.core;

import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.core.command.SilentMode;
import dev.riftal.creator.core.registry.EntityAttributes;
import dev.riftal.creator.core.registry.Registrar;
import dev.riftal.creator.core.sched.TickScheduler;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Supplier;

/**
 * Fabric side of the core library: hooks the loader events core defers into, then flushes the
 * deferred registrations. Called from {@link dev.riftal.creator.CreatorModsFabric}.
 *
 * <p>Feature code never touches this class.
 */
public final class CreatorModsFabricBootstrap {

    /** Command, tick and lifecycle hooks. Must run before {@link CreatorMods#init()}. */
    public static void hookEvents() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> CommandHelper.applyAll(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(TickScheduler::tick);

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TickScheduler.clear();
            SilentMode.reset();
        });
    }

    /** Pushes every {@link Registrar} entry into the real registries. Must run after init. */
    public static void flushRegistries() {
        Registrar.flushAll(new Registrar.Sink() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> void accept(ResourceKey<Registry<T>> registryKey, ResourceLocation id,
                                   Supplier<? extends T> value) {
                Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.get(registryKey.location());
                if (registry == null) {
                    throw new IllegalStateException("No built-in registry " + registryKey.location()
                            + " while registering " + id);
                }
                Registry.register(registry, id, value.get());
            }
        });

        for (EntityAttributes.Entry entry : EntityAttributes.pending()) {
            EntityType<? extends LivingEntity> type = entry.type().get();
            FabricDefaultAttributeRegistry.register(type, entry.builder().get());
        }
    }

    private CreatorModsFabricBootstrap() {
    }
}
