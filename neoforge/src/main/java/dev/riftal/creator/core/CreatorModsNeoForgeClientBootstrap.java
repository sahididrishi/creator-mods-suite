package dev.riftal.creator.core;

import dev.riftal.creator.core.client.ClientRenderers;
import dev.riftal.creator.core.hud.HudLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** NeoForge client side of the core library. Only reachable when {@code FMLEnvironment.dist} is client. */
final class CreatorModsNeoForgeClientBootstrap {

    static void hookEvents(IEventBus modBus) {
        modBus.addListener(CreatorModsNeoForgeClientBootstrap::onClientSetup);
        modBus.addListener(CreatorModsNeoForgeClientBootstrap::onRegisterGuiLayers);
        modBus.addListener(CreatorModsNeoForgeClientBootstrap::onRegisterRenderers);
        modBus.addListener(CreatorModsNeoForgeClientBootstrap::onRegisterLayerDefinitions);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(CreatorMods::initClient);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // initClient() is idempotent; call it here because the relative order of the client
        // registration events and FMLClientSetupEvent is not guaranteed.
        CreatorMods.initClient();
        for (HudLayers.Entry entry : HudLayers.all()) {
            event.registerAboveAll(entry.id(), entry.layer());
        }
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        CreatorMods.initClient();
        for (ClientRenderers.RendererEntry<?> entry : ClientRenderers.entityRenderers()) {
            entry.apply(new ClientRenderers.Sink() {
                @Override
                public <T extends Entity> void accept(EntityType<T> type, EntityRendererProvider<T> provider) {
                    event.registerEntityRenderer(type, provider);
                }
            });
        }
    }

    private static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        CreatorMods.initClient();
        for (ClientRenderers.LayerEntry entry : ClientRenderers.modelLayers()) {
            event.registerLayerDefinition(entry.location(), entry.definition()::get);
        }
    }

    private CreatorModsNeoForgeClientBootstrap() {
    }
}
