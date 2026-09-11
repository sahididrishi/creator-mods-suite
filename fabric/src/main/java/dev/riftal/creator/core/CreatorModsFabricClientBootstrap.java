package dev.riftal.creator.core;

import dev.riftal.creator.core.client.ClientRenderers;
import dev.riftal.creator.core.hud.HudLayers;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Fabric client side of the core library.
 *
 * <p>1.21.1 has no {@code HudElementRegistry} - that API landed in a later Fabric API line - so HUD
 * layers go through {@code HudRenderCallback.EVENT}, which hands us
 * {@code (GuiGraphics, DeltaTracker)} in Mojang mappings.
 */
public final class CreatorModsFabricClientBootstrap {

    public static void flush() {
        for (HudLayers.Entry entry : HudLayers.all()) {
            HudRenderCallback.EVENT.register(entry.layer()::render);
        }

        for (ClientRenderers.RendererEntry<?> entry : ClientRenderers.entityRenderers()) {
            entry.apply(new ClientRenderers.Sink() {
                @Override
                public <T extends Entity> void accept(EntityType<T> type, EntityRendererProvider<T> provider) {
                    EntityRendererRegistry.register(type, provider);
                }
            });
        }

        for (ClientRenderers.LayerEntry entry : ClientRenderers.modelLayers()) {
            EntityModelLayerRegistry.registerModelLayer(entry.location(), entry.definition()::get);
        }
    }

    private CreatorModsFabricClientBootstrap() {
    }
}
