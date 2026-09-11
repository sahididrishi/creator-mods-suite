package dev.riftal.creator.core;

import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.core.command.SilentMode;
import dev.riftal.creator.core.platform.NeoForgeAttachmentHelper;
import dev.riftal.creator.core.platform.NeoForgeNetworkHelper;
import dev.riftal.creator.core.registry.EntityAttributes;
import dev.riftal.creator.core.registry.Registrar;
import dev.riftal.creator.core.sched.TickScheduler;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.function.Supplier;

/**
 * NeoForge side of the core library.
 *
 * <p>Deferred registrations are flushed on the <em>mod</em> bus inside {@code RegisterEvent} and
 * {@code RegisterPayloadHandlersEvent}, which fire after mod construction - which is why
 * {@code Feature#registerContent()} must do all its declaring during {@link CreatorMods#init()} and
 * not later.
 *
 * <p>Feature code never touches this class.
 */
public final class CreatorModsNeoForgeBootstrap {

    public static void hookEvents(IEventBus modBus) {
        modBus.addListener(CreatorModsNeoForgeBootstrap::onRegister);
        modBus.addListener(CreatorModsNeoForgeBootstrap::onEntityAttributes);
        modBus.addListener(NeoForgeAttachmentHelper::onRegisterAttachments);
        modBus.addListener(NeoForgeNetworkHelper::onRegisterPayloads);
        modBus.addListener(CreatorModsNeoForgeBootstrap::onDedicatedServerSetup);

        NeoForge.EVENT_BUS.addListener(CreatorModsNeoForgeBootstrap::onCommands);
        NeoForge.EVENT_BUS.addListener(CreatorModsNeoForgeBootstrap::onServerTick);
        NeoForge.EVENT_BUS.addListener(CreatorModsNeoForgeBootstrap::onServerStopping);

        if (FMLEnvironment.dist.isClient()) {
            CreatorModsNeoForgeClientBootstrap.hookEvents(modBus);
        }
    }

    private static void onRegister(RegisterEvent event) {
        Registrar.flushAll(new Registrar.Sink() {
            @Override
            public <T> void accept(ResourceKey<Registry<T>> registryKey, ResourceLocation id,
                                   Supplier<? extends T> value) {
                if (!registryKey.equals(event.getRegistryKey())) {
                    return;
                }
                event.register(registryKey, id, value::get);
            }
        });
    }

    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        for (EntityAttributes.Entry entry : EntityAttributes.pending()) {
            EntityType<? extends LivingEntity> type = entry.type().get();
            event.put(type, entry.builder().get().build());
        }
    }

    private static void onDedicatedServerSetup(FMLDedicatedServerSetupEvent event) {
        event.enqueueWork(CreatorMods::initServer);
    }

    private static void onCommands(RegisterCommandsEvent event) {
        CommandHelper.applyAll(event.getDispatcher());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        TickScheduler.tick(event.getServer());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        TickScheduler.clear();
        SilentMode.reset();
    }

    private CreatorModsNeoForgeBootstrap() {
    }
}
