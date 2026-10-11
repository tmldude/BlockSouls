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
import io.github.tmldude.blocksouls.mobs.entity.Screamer;

/** Gaunt and tall, long thin limbs, a hinged jaw that drops open when it screams. */
public class ScreamerModel extends EntityModel<MobRenderState> {
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public ScreamerModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		head = body.getChild("head");
		jaw = head.getChild("jaw");
		rightArm = body.getChild("right_arm");
		leftArm = body.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-3, -13, -1.5f, 6, 13, 3),
				PartPose.offsetAndRotation(0, 9, 0, 0.12f, 0, 0));
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3, -7, -3, 6, 6, 6),
				PartPose.offset(0, -13, 0));
		head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(24, 0).addBox(-2.5f, 0, -5.5f, 5, 2, 5),
				PartPose.offset(0, -1, 2.5f));
		body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-1, -1, -1, 2, 17, 2),
				PartPose.offset(-4, -12, 0));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(48, 16).addBox(-1, -1, -1, 2, 17, 2),
				PartPose.offset(4, -12, 0));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-1.5f, 0, -1.5f, 3, 15, 3),
				PartPose.offset(-1.8f, 9, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 34).addBox(-1.5f, 0, -1.5f, 3, 15, 3),
				PartPose.offset(1.8f, 9, 0));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(MobRenderState state) {
		super.setupAnim(state);
		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		head.yRot = state.yRot * Mth.DEG_TO_RAD;
		head.xRot = state.xRot * Mth.DEG_TO_RAD;
		rightLeg.xRot = Mth.cos(pos * 0.6662f) * 1.1f * speed;
		leftLeg.xRot = Mth.cos(pos * 0.6662f + Mth.PI) * 1.1f * speed;
		rightArm.xRot = Mth.cos(pos * 0.6662f + Mth.PI) * 0.5f * speed;
		leftArm.xRot = Mth.cos(pos * 0.6662f) * 0.5f * speed;
		// Idle twitch.
		head.zRot = Mth.sin(state.ageInTicks * 0.31f) * Mth.sin(state.ageInTicks * 0.07f) * 0.12f;
		rightArm.zRot = 0.08f;
		leftArm.zRot = -0.08f;

		if (state.action == Screamer.ACTION_SCREAM) {
			// Jaw drops, head throws back, arms flare out.
			float t = Mth.clamp(state.actionTicks / 4f, 0, 1) * Mth.clamp((Screamer.SCREAM_TICKS - state.actionTicks) / 4f, 0, 1);
			jaw.xRot = 1.1f * t;
			head.xRot -= 0.6f * t;
			body.xRot -= 0.25f * t;
			rightArm.zRot += 1.0f * t;
			leftArm.zRot -= 1.0f * t;
			rightArm.xRot -= 0.4f * t;
			leftArm.xRot -= 0.4f * t;
		} else {
			jaw.xRot = 0.08f + Mth.sin(state.ageInTicks * 0.2f) * 0.05f;
		}
	}
}
