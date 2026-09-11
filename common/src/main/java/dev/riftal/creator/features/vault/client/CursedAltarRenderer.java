package dev.riftal.creator.features.vault.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.riftal.creator.features.vault.VaultFeature;
import dev.riftal.creator.features.vault.block.AltarState;
import dev.riftal.creator.features.vault.block.AltarStateMachine;
import dev.riftal.creator.features.vault.block.CursedAltarBlock;
import dev.riftal.creator.features.vault.block.entity.CursedAltarBlockEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draws the Cursed Altar's crystal, and makes it move.
 *
 * <p>The plan's 15-20 s hero beat is "the key is consumed, chains snap taut, the crystal rises and
 * spins, the room lights up purple". With the crystal baked into the blockstate model that beat was
 * a texture swap: four models differing by one line. Here the crystal is a real
 * {@link BlockEntityRenderer}, so it actually:
 *
 * <ul>
 *   <li>bobs gently while {@link AltarState#SEALED} - dormant, but not dead;</li>
 *   <li>rises and accelerates its spin across the 60-tick charge, driven by the same
 *       {@link AltarStateMachine#chargeProgress} the HUD bar uses, so the bar filling and the
 *       crystal climbing are the same number;</li>
 *   <li>holds at full height, full speed and full brightness while {@link AltarState#ACTIVE};</li>
 *   <li>drops back into its socket, stops and goes dark on {@link AltarState#SPENT}.</li>
 * </ul>
 *
 * <p>Everything is derived from the blockstate and the world clock rather than from a triggered
 * animation, so a late joiner, a relog or an {@code F3+T} shows the same thing as everyone else -
 * the failure mode the plan calls out for triggered GeckoLib clips.
 *
 * <p>Not GeckoLib: a {@code GeoBlockRenderer} needs exactly the same block-entity-renderer hook
 * this class needs (see {@code VaultBlockEntityRenderersMixin}) plus a hand-written {@code .geo.json}
 * and {@code .animation.json}, and {@code ASSETS.md} would still have to call both placeholder.
 * See {@code ASSETS.md}.
 *
 * <p><b>Client only.</b>
 */
public class CursedAltarRenderer implements BlockEntityRenderer<CursedAltarBlockEntity> {

    /** Height, in blocks above the block origin, the crystal sits at when dormant. */
    private static final float REST_HEIGHT = 0.875F;

    /** Extra height it climbs to while the Keeper is out. */
    private static final float LIFT = 0.5F;

    /** Degrees per tick at each end of the charge. */
    private static final float SPIN_IDLE = 0.6F;
    private static final float SPIN_ACTIVE = 9.0F;

    /** Bob amplitude in blocks, and its period in ticks. */
    private static final float BOB = 0.045F;
    private static final float BOB_PERIOD = 70.0F;

    /** The crystal is emissive once it is lit; a spent one takes the room's light like any block. */
    private static final int FULL_BRIGHT = LightTexture.pack(15, 15);

    private static final Map<AltarState, ResourceLocation> TEXTURES = new EnumMap<>(AltarState.class);

    static {
        for (AltarState state : AltarState.values()) {
            TEXTURES.put(state, ResourceLocation.fromNamespaceAndPath(VaultFeature.NAMESPACE,
                    "textures/entity/cursed_altar_crystal_" + state.getSerializedName() + ".png"));
        }
    }

    private final ModelPart crystal;

    public CursedAltarRenderer(BlockEntityRendererProvider.Context context) {
        this.crystal = context.bakeLayer(VaultClientSetup.ALTAR_CRYSTAL_LAYER)
                .getChild(CursedAltarModel.CRYSTAL);
    }

    @Override
    public void render(CursedAltarBlockEntity altar, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState blockState = altar.getBlockState();
        if (!(blockState.getBlock() instanceof CursedAltarBlock)) {
            return;
        }
        AltarState state = blockState.getValue(CursedAltarBlock.STATE);

        // Smooth clock. getLevel() is non-null for any block entity that is being rendered, but the
        // renderer is also driven from the item/preview path in some mods, so fall back to 0.
        float time = altar.getLevel() == null
                ? 0.0F
                : (float) (altar.getLevel().getGameTime() % 24000L) + partialTick;

        float charge = chargeFraction(altar, state, partialTick);
        float height = REST_HEIGHT + LIFT * charge
                + BOB * Mth.sin(time / BOB_PERIOD * (float) (Math.PI * 2.0));
        float spin = Mth.lerp(charge, SPIN_IDLE, SPIN_ACTIVE) * time;

        poseStack.pushPose();
        poseStack.translate(0.5F, height, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        // A slow counter-tilt so the facets catch the light instead of reading as a flat square.
        poseStack.mulPose(Axis.XP.rotationDegrees(12.0F * charge));
        // ModelPart geometry is in 1/16-block units, like every vanilla entity model.
        poseStack.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);

        int light = state == AltarState.SPENT ? packedLight : FULL_BRIGHT;
        VertexConsumer buffer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(TEXTURES.get(state)));
        this.crystal.render(poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /**
     * 0 while dormant, 0..1 across the charge, 1 once the Keeper is out, 0 again once spent.
     *
     * <p>{@code chargeTicks} only advances on the server, and the client's copy of the block entity
     * never sees it, so CHARGING is interpolated from the blockstate's own age instead: the client
     * knows exactly when the state flipped, because that is when the block update arrived.
     */
    private static float chargeFraction(CursedAltarBlockEntity altar, AltarState state,
                                        float partialTick) {
        return switch (state) {
            case SEALED, SPENT -> 0.0F;
            case ACTIVE -> 1.0F;
            case CHARGING -> {
                float ticks = altar.clientStateTicks() + partialTick;
                yield Mth.clamp(ticks / AltarStateMachine.CHARGE_TICKS, 0.0F, 1.0F);
            }
        };
    }

    @Override
    public int getViewDistance() {
        // The treasure room is big and the plan's slow pan starts from the doorway.
        return 96;
    }
}
