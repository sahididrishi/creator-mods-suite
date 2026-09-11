package dev.riftal.creator.core.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Loader-neutral HUD registration.
 *
 * <pre>{@code
 * // inside Feature#initClient()
 * HudLayers.register(rl("take_timer"), (graphics, delta) -> {
 *     if (HudLayers.hudHidden()) return;
 *     HudText.drawShadowed(graphics, "REC 00:12", 8, 8, 0xFFFF5555);
 * });
 * }</pre>
 *
 * <p>NeoForge flushes these into {@code RegisterGuiLayersEvent#registerAboveAll}; Fabric into
 * {@code HudRenderCallback.EVENT} (1.21.1 has no {@code HudElementRegistry} - that arrived later).
 * Fabric therefore cannot honour relative ordering; layers render in registration order, above the
 * vanilla HUD, on both loaders.
 *
 * <p><b>Client only.</b> See {@link HudLayer}.
 */
public final class HudLayers {

    private static final List<Entry> ENTRIES = new ArrayList<>();

    /** One registered layer. Loader glue only. */
    public record Entry(ResourceLocation id, HudLayer layer) {
    }

    /** Registers a layer that draws above the whole vanilla HUD. */
    public static void register(ResourceLocation id, HudLayer layer) {
        ENTRIES.add(new Entry(id, layer));
    }

    /**
     * Registers a layer. {@code below} names the vanilla layer to sit under on NeoForge (see
     * {@code VanillaGuiLayers}); Fabric ignores it.
     */
    public static void registerBelow(ResourceLocation below, ResourceLocation id, HudLayer layer) {
        ENTRIES.add(new Entry(id, layer));
    }

    /** Loader glue only. */
    public static List<Entry> all() {
        return List.copyOf(ENTRIES);
    }

    /** True while F1 is hiding the HUD, or during a screenshot. Check it in every layer. */
    public static boolean hudHidden() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null || mc.options.hideGui;
    }

    private HudLayers() {
    }
}
