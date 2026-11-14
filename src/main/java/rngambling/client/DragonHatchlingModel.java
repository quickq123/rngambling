package rngambling.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.util.Mth;
import rngambling.RNGambling;

/**
 * The Dragon Hatchling: a long body on four short legs, a horned head with a snout, a spiny back,
 * a two-part tail with a fin, and folded wings raised over its back that flap a little as it walks. The walk is the
 * wolf's (legs swinging in pairs). Texture layout: tools/make_textures.py, dragon_hatchling().
 */
public class DragonHatchlingModel extends EntityModel<WolfRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(RNGambling.id("dragon_hatchling"), "main");

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightHindLeg;
    private final ModelPart leftHindLeg;
    private final ModelPart rightFrontLeg;
    private final ModelPart leftFrontLeg;
    private final ModelPart tail;
    private final ModelPart tailTip;
    private final ModelPart rightWing;
    private final ModelPart leftWing;

    public DragonHatchlingModel(ModelPart root) {
        super(root);
        head = root.getChild("head");
        body = root.getChild("body");
        rightHindLeg = root.getChild("right_hind_leg");
        leftHindLeg = root.getChild("left_hind_leg");
        rightFrontLeg = root.getChild("right_front_leg");
        leftFrontLeg = root.getChild("left_front_leg");
        tail = root.getChild("tail");
        tailTip = tail.getChild("tail_tip");
        rightWing = root.getChild("right_wing");
        leftWing = root.getChild("left_wing");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3f, -4f, -5f, 6, 5, 5)       // head
                        .texOffs(24, 0).addBox(-2f, -2f, -9f, 4, 3, 4)      // snout
                        .texOffs(42, 0).addBox(-2.5f, -7f, -2f, 1, 3, 1)    // horns
                        .texOffs(42, 0).addBox(1.5f, -7f, -2f, 1, 3, 1),
                PartPose.offset(0f, 13f, -6f));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 10).addBox(-3.5f, -3f, -6f, 7, 6, 13)
                        .texOffs(52, 10).addBox(-0.5f, -5f, -4f, 1, 2, 2)   // spines along the back
                        .texOffs(52, 10).addBox(-0.5f, -5f, -0.5f, 1, 2, 2)
                        .texOffs(52, 10).addBox(-0.5f, -5f, 3f, 1, 2, 2),
                PartPose.offset(0f, 15f, 0f));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(40, 10).addBox(-1.5f, 0f, -1.5f, 3, 6, 3);
        root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-2.5f, 18f, 5f));
        root.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(2.5f, 18f, 5f));
        root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-2.5f, 18f, -4f));
        root.addOrReplaceChild("left_front_leg", leg, PartPose.offset(2.5f, 18f, -4f));
        PartDefinition tail = root.addOrReplaceChild("tail", CubeListBuilder.create()
                        .texOffs(0, 29).addBox(-1.5f, -1.5f, 0f, 3, 3, 6),
                PartPose.offsetAndRotation(0f, 14f, 6.5f, -0.35f, 0f, 0f));
        tail.addOrReplaceChild("tail_tip", CubeListBuilder.create()
                        .texOffs(18, 29).addBox(-1f, -1f, 0f, 2, 2, 5)
                        .texOffs(32, 29).addBox(-0.5f, -3f, 2f, 1, 3, 3),   // fin
                PartPose.offsetAndRotation(0f, 0f, 5.5f, 0.3f, 0f, 0f));
        root.addOrReplaceChild("right_wing", CubeListBuilder.create().mirror()
                        .texOffs(0, 40).addBox(-1f, -5f, 0f, 1, 5, 8),
                PartPose.offsetAndRotation(-2.5f, 12.5f, -3f, 0f, 0f, -0.35f));
        root.addOrReplaceChild("left_wing", CubeListBuilder.create()
                        .texOffs(0, 40).addBox(0f, -5f, 0f, 1, 5, 8),
                PartPose.offsetAndRotation(2.5f, 12.5f, -3f, 0f, 0f, 0.35f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(WolfRenderState state) {
        super.setupAnim(state);
        float pos = state.walkAnimationPos;
        float speed = state.walkAnimationSpeed;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        // Tail sways as it walks (still when angry), and wags with the wolf's tail angle (lower when hurt).
        tail.yRot = state.isAngry ? 0f : Mth.cos(pos * 0.6662f) * 0.6f * speed;
        tail.xRot = -0.35f + (state.tailAngle - (float) (Math.PI / 5)) * 0.5f;
        tailTip.yRot = tail.yRot * 0.8f;
        // Folded wings, lifting a little in time with the steps, and slowly at rest.
        float flap = Mth.sin(state.ageInTicks * 0.1f) * 0.05f + Mth.cos(pos * 0.6662f * 2) * 0.2f * speed;
        rightWing.zRot = -0.35f - flap;
        leftWing.zRot = 0.35f + flap;
        if (state.isSitting) {
            body.y += 2f;
            head.y += 2f;
            rightWing.y += 2f;
            leftWing.y += 2f;
            tail.y += 3.5f;
            rightHindLeg.xRot = leftHindLeg.xRot = -(float) Math.PI / 2;
            rightHindLeg.y += 4f;
            leftHindLeg.y += 4f;
            rightHindLeg.z -= 2f;
            leftHindLeg.z -= 2f;
            rightFrontLeg.xRot = leftFrontLeg.xRot = -0.35f;
        } else {
            rightHindLeg.xRot = Mth.cos(pos * 0.6662f) * 1.4f * speed;
            leftHindLeg.xRot = Mth.cos(pos * 0.6662f + Mth.PI) * 1.4f * speed;
            rightFrontLeg.xRot = Mth.cos(pos * 0.6662f + Mth.PI) * 1.4f * speed;
            leftFrontLeg.xRot = Mth.cos(pos * 0.6662f) * 1.4f * speed;
        }
    }
}
