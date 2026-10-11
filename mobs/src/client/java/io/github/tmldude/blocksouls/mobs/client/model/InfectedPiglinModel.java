package io.github.tmldude.blocksouls.mobs.client.model;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;

/**
 * Piglin proportions (wide snouted head, floppy ears) overgrown with fleshy lumps on the head, back and one
 * arm. Humanoid rig so it can hold its gold sword.
 */
public class InfectedPiglinModel extends HumanoidModel<HumanoidRenderState> {
	private final ModelPart rightEar;
	private final ModelPart leftEar;

	public InfectedPiglinModel(ModelPart root) {
		super(root);
		rightEar = head.getChild("right_ear");
		leftEar = head.getChild("left_ear");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-5, -8, -4, 10, 8, 8)
				.texOffs(31, 1).addBox(-2, -4, -5, 4, 4, 1)
				.texOffs(2, 4).addBox(2, -2, -5, 1, 2, 1)
				.texOffs(2, 0).addBox(-3, -2, -5, 1, 2, 1), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(51, 6).addBox(0, 0, -2, 1, 5, 4),
				PartPose.offsetAndRotation(4.5f, -6, 0, 0, 0, -0.5236f));
		head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(39, 6).addBox(-1, 0, -2, 1, 5, 4),
				PartPose.offsetAndRotation(-4.5f, -6, 0, 0, 0, 0.5236f));
		head.addOrReplaceChild("growth", CubeListBuilder.create().texOffs(44, 32).addBox(-2, -3, -2, 4, 3, 5)
				.texOffs(44, 40).addBox(1, -2, 0, 3, 2, 3), PartPose.offsetAndRotation(-1, -8, -1, 0.2f, 0, 0.3f));
		PartDefinition body = root.getChild("body");
		body.addOrReplaceChild("growth", CubeListBuilder.create().texOffs(0, 32).addBox(-3, -3, 0, 6, 6, 3)
				.texOffs(18, 32).addBox(0, 2, 1, 4, 4, 2), PartPose.offsetAndRotation(0, 4, 2, 0, 0, 0.15f));
		root.getChild("left_arm").addOrReplaceChild("growth", CubeListBuilder.create().texOffs(30, 40).addBox(-1, -2, -2, 3, 5, 4),
				PartPose.offset(2.5f, 1, 0));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(HumanoidRenderState state) {
		super.setupAnim(state);
		float t = state.ageInTicks * 0.1f + state.walkAnimationPos * 0.5f;
		float amp = 0.08f + state.walkAnimationSpeed * 0.4f;
		leftEar.zRot = -0.5236f - Mth.cos(t * 1.2f) * amp;
		rightEar.zRot = 0.5236f + Mth.cos(t) * amp;
		// Twitchy, sick head.
		head.zRot += Mth.sin(state.ageInTicks * 0.43f) * Mth.sin(state.ageInTicks * 0.11f) * 0.1f;
	}

	@Override
	protected void setupAttackAnimation(HumanoidRenderState state) {
		super.setupAttackAnimation(state);
		// Off arm claws forward like the infected do.
		AnimationUtils.bobModelPart(leftArm, state.ageInTicks, -1);
		leftArm.xRot -= 0.9f;
	}
}
