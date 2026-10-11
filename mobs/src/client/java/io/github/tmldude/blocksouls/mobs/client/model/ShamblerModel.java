package io.github.tmldude.blocksouls.mobs.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import io.github.tmldude.blocksouls.mobs.client.render.MobRenderState;

/** Hunched humanoid, head lolling, the right arm longer and dragging. Humanoid 64×64 UVs. */
public class ShamblerModel extends EntityModel<MobRenderState> {
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public ShamblerModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		head = body.getChild("head");
		rightArm = body.getChild("right_arm");
		leftArm = body.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// Body pivots at the hips so the hunch leans the whole upper half forward.
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4, -12, -2, 8, 12, 4),
				PartPose.offsetAndRotation(0, 12, 0, 0.35f, 0, 0));
		body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8),
				PartPose.offsetAndRotation(0, -12, -1, -0.2f, 0, 0.25f));
		body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, 4, 15, 4),
				PartPose.offset(-5, -10, 0));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48).addBox(-1, -2, -2, 4, 12, 4),
				PartPose.offset(5, -10, 0));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 12, 4),
				PartPose.offset(-1.9f, 12, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2, 0, -2, 4, 12, 4),
				PartPose.offset(1.9f, 12, 0));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(MobRenderState state) {
		super.setupAnim(state);
		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		head.yRot += state.yRot * Mth.DEG_TO_RAD * 0.7f;
		head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5f;

		rightLeg.xRot = Mth.cos(pos * 0.6662f) * 1.2f * speed;
		leftLeg.xRot = Mth.cos(pos * 0.6662f + Mth.PI) * 1.2f * speed;
		// A lurching gait: the body rolls with each step.
		body.zRot = Mth.cos(pos * 0.6662f) * 0.12f * speed;

		// Left arm reaches out; the long right arm hangs and swings, rising only when it attacks.
		float reach = state.aggressive ? -1.6f : -1.1f;
		leftArm.xRot = reach - 0.35f + Mth.sin(state.ageInTicks * 0.067f) * 0.08f;
		rightArm.xRot = -0.35f + Mth.cos(pos * 0.6662f + Mth.PI) * 0.6f * speed;
		if (state.swing > 0) {
			float s = Mth.sin(state.swing * Mth.PI);
			rightArm.xRot -= s * 2.2f;
			leftArm.xRot -= s * 0.6f;
		}
		rightArm.zRot = 0.1f + Mth.cos(state.ageInTicks * 0.09f) * 0.04f;
		leftArm.zRot = -0.1f - Mth.cos(state.ageInTicks * 0.09f) * 0.04f;
	}
}
