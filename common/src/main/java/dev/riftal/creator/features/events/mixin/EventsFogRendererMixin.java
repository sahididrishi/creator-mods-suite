package dev.riftal.creator.features.events.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.events.EventsFeature;
import dev.riftal.creator.features.events.client.ClientEventState;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tints the atmospheric fog toward the running event's colour - the other half of the blood moon's
 * flush, and the only thing that reads as "the void is coming up at you" when the camera is
 * pointed at the ground.
 *
 * <p>{@code ClientLevel#getSkyColor} only paints the sky dome, and {@code LevelRenderer#renderSky}
 * only calls it for {@code SkyType.NORMAL}: without this the haze around the player stays vanilla
 * grey-blue while the dome goes red, and in a cave or the Nether the tint contributes nothing at
 * all. Vanilla derives {@code fogRed/fogGreen/fogBlue} from a {@code CubicSampler} over the biome
 * fog colours (line 102 of {@code FogRenderer}), so the tint has to be applied on top afterwards.
 *
 * <p><b>One mixin for both loaders, on purpose.</b> The plan suggested NeoForge use
 * {@code ViewportEvent.ComputeFogColor} instead - but this config's {@code "client"} list is shared
 * by both loader jars, so shipping both would double-apply the tint on NeoForge. Injecting into the
 * vanilla method on both loaders gives one code path and one visual result, which is what the
 * plan's QA line ("compare sky/fog on both loaders - they must match") is actually asking for.
 *
 * <p>Vanilla's own last statement is {@code RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0)},
 * so it is repeated here after the write; {@code FogRenderer#levelFogColor} reads the fields again
 * later in the frame and picks the tint up on its own.
 *
 * <p>Client only: listed under {@code "client"} in {@code creatormods-events.mixins.json}.
 */
@Mixin(FogRenderer.class)
public abstract class EventsFogRendererMixin {

    @Shadow
    private static float fogRed;

    @Shadow
    private static float fogGreen;

    @Shadow
    private static float fogBlue;

    @Inject(
            method = "setupColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)V",
            at = @At("TAIL"))
    private static void creator_events$tintFog(Camera activeRenderInfo, float partialTicks,
                                               ClientLevel level, int renderDistanceChunks,
                                               float bossColorModifier, CallbackInfo ci) {
        if (!CreatorMods.isEnabled(EventsFeature.ID)) {
            return;
        }
        if (ClientEventState.shownStrength() <= 0.0F) {
            return;
        }
        // Underwater, lava and powder-snow fog are their own statement; the plan's QA note calls
        // them out explicitly. Leave them alone.
        if (activeRenderInfo.getFluidInCamera() != FogType.NONE) {
            return;
        }
        Vec3 tinted = ClientEventState.tintColour(new Vec3(fogRed, fogGreen, fogBlue));
        fogRed = (float) tinted.x;
        fogGreen = (float) tinted.y;
        fogBlue = (float) tinted.z;
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }
}
