package dev.riftal.creator.features.evolve.client.render;

import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.entity.ApexBeast;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Apex beast: a hunched, long-armed biped about 4.7 blocks tall, built with vanilla's
 * {@code LayerDefinition} pipeline rather than GeckoLib. Animation is the same trigonometry vanilla
 * uses for its own mobs - a walk cycle from {@code limbSwing}, an idle sway from {@code ageInTicks},
 * a jaw that opens for the roar - which is enough for the walk / idle / roar beats in the plan and
 * costs no extra dependency.
 *
 * <p>Deliberately generic over {@code LivingEntity}: the same baked model draws the real
 * {@link ApexBeast} entity <em>and</em> stands in for a stage-5 player in
 * {@link PlayerRenderSwap}.
 *
 * <p>Model space: y grows downward, the feet sit at y = 24, so the crown of the horns at y = -54 is
 * (24 + 54) / 16 = 4.875 blocks up.
 */
public class ApexBeastModel<T extends LivingEntity> extends HierarchicalModel<T> {

    /** Registered through {@code ClientRenderers.modelLayer} in {@code EvolveClient}. */
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(EvolveFeature.id("apex_beast"), "main");

    /** Skin, hand-written placeholder for now. See {@code ASSETS.md}. */
    public static final ResourceLocation TEXTURE = EvolveFeature.id("textures/entity/apex_beast.png");

    private static final float DEG = (float) (Math.PI / 180.0D);

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart tail;

    public ApexBeastModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.jaw = this.head.getChild("jaw");
        this.rightArm = this.body.getChild("right_arm");
        this.leftArm = this.body.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.tail = this.body.getChild("tail");
    }

    /** The mesh. Texture is 128x128. */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-10.0F, -30.0F, -7.0F, 20.0F, 30.0F, 14.0F),
                PartPose.offset(0.0F, -6.0F, 0.0F));

        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 46)
                        .addBox(-7.0F, -15.0F, -16.0F, 14.0F, 15.0F, 18.0F),
                PartPose.offset(0.0F, -30.0F, -2.0F));

        head.addOrReplaceChild("jaw",
                CubeListBuilder.create().texOffs(46, 82)
                        .addBox(-5.0F, 0.0F, -12.0F, 10.0F, 4.0F, 12.0F),
                PartPose.offset(0.0F, -3.0F, -2.0F));

        head.addOrReplaceChild("right_horn",
                CubeListBuilder.create().texOffs(92, 82)
                        .addBox(-1.5F, -8.0F, -1.5F, 3.0F, 8.0F, 3.0F),
                PartPose.offsetAndRotation(-5.0F, -14.0F, -5.0F, -0.3F, 0.0F, -0.25F));

        head.addOrReplaceChild("left_horn",
                CubeListBuilder.create().texOffs(92, 82).mirror()
                        .addBox(-1.5F, -8.0F, -1.5F, 3.0F, 8.0F, 3.0F),
                PartPose.offsetAndRotation(5.0F, -14.0F, -5.0F, -0.3F, 0.0F, 0.25F));

        body.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(70, 40)
                        .addBox(-3.5F, -2.0F, -3.5F, 7.0F, 32.0F, 7.0F),
                PartPose.offset(-11.0F, -26.0F, 0.0F));

        body.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(70, 40).mirror()
                        .addBox(-3.5F, -2.0F, -3.5F, 7.0F, 32.0F, 7.0F),
                PartPose.offset(11.0F, -26.0F, 0.0F));

        body.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(0, 82)
                        .addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F),
                PartPose.offsetAndRotation(0.0F, -8.0F, 7.0F, -0.35F, 0.0F, 0.0F));

        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(70, 0)
                        .addBox(-4.0F, 0.0F, -4.0F, 8.0F, 30.0F, 8.0F),
                PartPose.offset(-5.0F, -6.0F, 0.0F));

        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(70, 0).mirror()
                        .addBox(-4.0F, 0.0F, -4.0F, 8.0F, 30.0F, 8.0F),
                PartPose.offset(5.0F, -6.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        float swing = Math.min(limbSwingAmount, 1.0F);
        float idle = Mth.cos(ageInTicks * 0.09F) * 0.05F;

        this.head.xRot = Mth.clamp(headPitch, -60.0F, 60.0F) * DEG + idle;
        this.head.yRot = Mth.clamp(netHeadYaw, -70.0F, 70.0F) * DEG;
        this.head.zRot = 0.0F;

        this.body.xRot = 0.12F + idle * 0.5F;
        this.body.yRot = Mth.sin(ageInTicks * 0.045F) * 0.03F;

        this.rightLeg.xRot = Mth.cos(limbSwing * 0.5F) * 1.1F * swing;
        this.leftLeg.xRot = Mth.cos(limbSwing * 0.5F + (float) Math.PI) * 1.1F * swing;

        this.rightArm.xRot = Mth.cos(limbSwing * 0.5F + (float) Math.PI) * 0.9F * swing - 0.15F;
        this.leftArm.xRot = Mth.cos(limbSwing * 0.5F) * 0.9F * swing - 0.15F;
        this.rightArm.zRot = 0.15F + idle;
        this.leftArm.zRot = -0.15F - idle;

        this.tail.xRot = -0.35F + Mth.cos(ageInTicks * 0.12F) * 0.12F + swing * 0.25F;
        this.tail.yRot = Mth.sin(ageInTicks * 0.1F) * 0.25F;

        float roar = roarAmount(entity, ageInTicks);
        this.jaw.xRot = 0.12F + roar * 0.7F;
        if (roar > 0.0F) {
            this.head.xRot -= roar * 0.45F;
            this.rightArm.xRot -= roar * 0.9F;
            this.leftArm.xRot -= roar * 0.9F;
            this.rightArm.zRot += roar * 0.35F;
            this.leftArm.zRot -= roar * 0.35F;
        }

        // attackTime is set by LivingEntityRenderer for the real entity, and by PlayerRenderSwap
        // for a stage-5 player. 0..1 over one swing.
        if (this.attackTime > 0.0F) {
            float swingArc = Mth.sin(this.attackTime * (float) Math.PI);
            this.rightArm.xRot -= swingArc * 1.6F;
            this.body.yRot -= swingArc * 0.25F;
        }
    }

    /** 0..1. Real beasts roar at a target; a transforming or sprinting player does not. */
    private static float roarAmount(LivingEntity entity, float ageInTicks) {
        if (entity instanceof ApexBeast beast && beast.isRoaring()) {
            return 0.5F + Mth.cos(ageInTicks * 0.4F) * 0.5F;
        }
        return 0.0F;
    }
}
