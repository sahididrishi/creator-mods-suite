package dev.riftal.creator.features.evolve.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.client.ClientEvolutionCache;
import dev.riftal.creator.features.evolve.data.EvolutionData;
import dev.riftal.creator.features.evolve.entity.ApexBeast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;

/**
 * Draws the Apex beast where a stage-5 player's body would be.
 *
 * <p>Called from {@code EvolvePlayerRendererMixin}, which skips the {@code LivingEntityRenderer}
 * call inside {@code PlayerRenderer#render}. Skipping it costs the body, its render layers (armour,
 * held items, cape, elytra) and the name tag - the mixin puts the name tag back. The shadow and the
 * fire overlay are drawn by {@code EntityRenderDispatcher} before {@code render} is ever called, so
 * they survive untouched.
 *
 * <p>The beast itself is a GeckoLib model, so it needs a {@code GeoAnimatable} to animate:
 * {@link BeastProxyManager} keeps one never-spawned {@link ApexBeast} per Apex player in step with
 * that player, and this class hands it to that entity type's own registered renderer. The beast a
 * player wears and the beast you can {@code /summon} are therefore literally the same renderer,
 * model, animations and shadow.
 *
 * <p><b>Client only.</b>
 */
public final class PlayerRenderSwap {

    /**
     * The mesh is authored {@link ApexBeast#MODEL_HEIGHT} blocks tall, which is exactly a stage-5
     * player's hitbox height. Scaling by {@code player.getScale() * RENDER_SCALE} therefore draws
     * the beast at world scale 1 at Apex, and keeps it the same height as whatever body the player
     * currently has for {@code /evolve model @s on} at a lower stage.
     */
    private static final float RENDER_SCALE = 1.8F / ApexBeast.MODEL_HEIGHT;

    /** True when this player should be drawn as the beast rather than as their skin. */
    public static boolean shouldSwap(AbstractClientPlayer player) {
        if (!EvolveFeature.isReady() || player.isSpectator()) {
            BeastProxyManager.forget(player.getUUID());
            return false;
        }
        EvolutionData state = ClientEvolutionCache.state(player.getUUID());
        if (state.transforming() || !state.usesBeastModel()) {
            // Mid-sequence the player keeps their own body; the beast appears when it lands.
            BeastProxyManager.forget(player.getUUID());
            return false;
        }
        return true;
    }

    /**
     * Draws the beast at the player's position. The pose stack arrives positioned at the player's
     * feet, exactly as {@code PlayerRenderer#render} received it, so the proxy's own renderer can
     * take it from there.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void renderBeast(AbstractClientPlayer player, float partialTicks, PoseStack poseStack,
                                   MultiBufferSource buffer, int packedLight) {
        ApexBeast proxy = BeastProxyManager.proxyFor(player, partialTicks);
        if (proxy == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderer renderer = minecraft.getEntityRenderDispatcher().getRenderer(proxy);
        if (renderer == null) {
            return;
        }

        float bodyYaw = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot);
        float scale = player.getScale() * RENDER_SCALE;

        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        // getRenderer returns EntityRenderer<? super ApexBeast>; a raw call is the only way to hand
        // it back the very entity it came from without an unchecked cast of the renderer's type
        // parameter. The proxy is an ApexBeast, so this is always the right renderer.
        renderer.render(proxy, bodyYaw, partialTicks, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    /**
     * A trimmed version of {@code LivingEntityRenderer#shouldShowName} - distance, F1, camera entity
     * and invisibility. Team name-tag visibility rules are not reproduced; on a two-player recording
     * server there are no teams.
     */
    public static boolean shouldShowName(AbstractClientPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        double distanceSq = minecraft.getEntityRenderDispatcher().distanceToSqr(player);
        float limit = player.isDiscrete() ? 32.0F : 64.0F;
        if (distanceSq >= limit * limit) {
            return false;
        }
        return Minecraft.renderNames()
                && player != minecraft.getCameraEntity()
                && !player.isInvisibleTo(minecraft.player)
                && !player.isVehicle();
    }

    private PlayerRenderSwap() {
    }
}
