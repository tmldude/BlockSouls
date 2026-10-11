package io.github.tmldude.blocksouls.mobs.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.client.render.MobRenderState;
import io.github.tmldude.blocksouls.mobs.entity.Bloater;

/** A swollen sack of a body on stubby legs, tiny head and arms. Pulses at rest, inflates during its telegraph. */
public class BloaterModel extends EntityModel<MobRenderState> {
	private final ModelPart belly;
	private final ModelPart head;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	private final ModelPart rightArm;
	private final ModelPart leftArm;

	public BloaterModel(ModelPart root) {
		super(root);
		belly = root.getChild("belly");
		head = root.getChild("head");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
		rightArm = root.getChild("right_arm");
		leftArm = root.getChild("left_arm");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// Pivot at the belly's center so it swells evenly.
		PartDefinition belly = root.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(0, 0).addBox(-9, -9, -9, 18, 18, 18),
				PartPose.offset(0, 11, 0));
		belly.addOrReplaceChild("sac_right", CubeListBuilder.create().texOffs(0, 36).addBox(-3, -3, -3, 5, 6, 6),
				PartPose.offset(-9, 1, -2));
		belly.addOrReplaceChild("sac_front", CubeListBuilder.create().texOffs(22, 36).addBox(-3, -3, -2, 6, 6, 4),
				PartPose.offset(2, 3, -9));
		belly.addOrReplaceChild("sac_back", CubeListBuilder.create().texOffs(42, 36).addBox(-3, -3, -1, 7, 7, 4),
				PartPose.offset(-1, -3, 9));
		root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(72, 0).addBox(-3, -5, -3, 6, 5, 6),
				PartPose.offset(0, 3, -3));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(72, 12).addBox(-2, 0, -2, 4, 4, 4),
				PartPose.offset(-4, 20, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(72, 12).mirror().addBox(-2, 0, -2, 4, 4, 4),
				PartPose.offset(4, 20, 0));
		root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(72, 22).addBox(-1.5f, 0, -1.5f, 3, 8, 3),
				PartPose.offsetAndRotation(-9.5f, 6, -2, 0, 0, 0.3f));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(72, 22).mirror().addBox(-1.5f, 0, -1.5f, 3, 8, 3),
				PartPose.offsetAndRotation(9.5f, 6, -2, 0, 0, -0.3f));
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(MobRenderState state) {
		super.setupAnim(state);
		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.5f;
		head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5f;
		rightLeg.xRot = Mth.cos(pos * 0.9f) * 1.0f * speed;
		leftLeg.xRot = Mth.cos(pos * 0.9f + Mth.PI) * 1.0f * speed;
		// Waddle.
		belly.zRot = Mth.cos(pos * 0.9f) * 0.08f * speed;
		rightArm.xRot = Mth.cos(pos * 0.9f + Mth.PI) * 0.4f * speed;
		leftArm.xRot = Mth.cos(pos * 0.9f) * 0.4f * speed;

		float pulse = 1 + Mth.sin(state.ageInTicks * 0.12f) * 0.03f;
		float swell = 0;
		if (state.action == Bloater.ACTION_SWELL) {
			swell = Mth.clamp(state.actionTicks / BlockSoulsMobs.CONFIG.bloater.swellTicks, 0, 1);
			// Shudders faster as it nears bursting.
			pulse += Mth.sin(state.ageInTicks * (1.2f + swell * 2f)) * 0.05f * swell;
			rightArm.zRot += swell * 0.8f;
			leftArm.zRot -= swell * 0.8f;
		}
		float scale = pulse + swell * 0.35f;
		belly.xScale = scale;
		belly.yScale = pulse + swell * 0.25f;
		belly.zScale = scale;
		head.y -= swell * 2.5f;
	}
}
