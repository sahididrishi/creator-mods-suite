package dev.riftal.creator.core.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Loader-neutral client renderer registration.
 *
 * <pre>{@code
 * // inside Feature#initClient()
 * ClientRenderers.entityRenderer(COLOSSUS, ColossusRenderer::new);
 * ClientRenderers.modelLayer(COLOSSUS_LAYER, ColossusModel::createBodyLayer);
 * }</pre>
 *
 * <p>Flushed into Fabric's {@code EntityRendererRegistry} / {@code EntityModelLayerRegistry} and
 * NeoForge's {@code EntityRenderersEvent.RegisterRenderers} / {@code RegisterLayerDefinitions}.
 * GeckoLib renderers register through here too - they are ordinary {@code EntityRenderer}s.
 *
 * <p><b>Client only.</b>
 */
public final class ClientRenderers {

    private static final List<RendererEntry<?>> RENDERERS = new ArrayList<>();
    private static final List<LayerEntry> LAYERS = new ArrayList<>();

    /** One declared entity renderer. Loader glue only. */
    public record RendererEntry<T extends Entity>(Supplier<? extends EntityType<T>> type,
                                                  EntityRendererProvider<T> provider) {
        /** Re-captures {@code T} so loader code can call a generic registration method. */
        public void apply(Sink sink) {
            sink.accept(type.get(), provider);
        }
    }

    /** One declared model layer. Loader glue only. */
    public record LayerEntry(ModelLayerLocation location, Supplier<LayerDefinition> definition) {
    }

    /** Implemented by each loader. */
    @FunctionalInterface
    public interface Sink {
        <T extends Entity> void accept(EntityType<T> type, EntityRendererProvider<T> provider);
    }

    public static <T extends Entity> void entityRenderer(Supplier<? extends EntityType<T>> type,
                                                         EntityRendererProvider<T> provider) {
        RENDERERS.add(new RendererEntry<>(type, provider));
    }

    public static void modelLayer(ModelLayerLocation location, Supplier<LayerDefinition> definition) {
        LAYERS.add(new LayerEntry(location, definition));
    }

    /** Loader glue only. */
    public static List<RendererEntry<?>> entityRenderers() {
        return List.copyOf(RENDERERS);
    }

    /** Loader glue only. */
    public static List<LayerEntry> modelLayers() {
        return List.copyOf(LAYERS);
    }

    private ClientRenderers() {
    }
}
