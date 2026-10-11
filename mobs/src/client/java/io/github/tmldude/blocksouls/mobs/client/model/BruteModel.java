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

/**
 * Massive shoulders and long knuckle-dragging arms on short legs, a tiny head sunk into the chest. The Mutant
 * Brute uses the same rig with growths added ({@link #createMutantLayer()}).
 */
public class BruteModel extends EntityModel<MobRenderState> {
	protected final ModelPart body;
	protected final ModelPart head;
	protected final ModelPart rightArm;
	protected final ModelPart leftArm;
	protected final ModelPart rightLeg;
	protected final ModelPart leftLeg;

	public BruteModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		head = body.getChild("head");
		rightArm = body.getChild("right_arm");
		leftArm = body.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
	}

	private static MeshDefinition createMesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-9, -18, -6, 18, 18, 12)
				.texOffs(60, 26).addBox(-6, -4, -5, 12, 6, 10),
				PartPose.offsetAndRotation(0, 10, 0, 0.3f, 0, 0));
		body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(60, 0).addBox(-3, -5, -5, 6, 6, 6),
				PartPose.offsetAndRotation(0, -16, -6, -0.3f, 0, 0));
		body.addOrReplaceChild("right_arm", CubeListBuilder.create()
				.texOffs(0, 30).addBox(-4, -3, -4, 7, 24, 8)
				.texOffs(84, 0).addBox(-5, -5, -5, 9, 7, 10),
				PartPose.offset(-12, -14, 0));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create()
				.texOffs(0, 30).mirror().addBox(-3, -3, -4, 7, 24, 8)
				.texOffs(84, 0).mirror().addBox(-4, -5, -5, 9, 7, 10),
				PartPose.offset(12, -14, 0));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(30, 30).addBox(-3.5f, 0, -3.5f, 7, 14, 7),
				PartPose.offset(-5, 10, 1));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(30, 30).mirror().addBox(-3.5f, 0, -3.5f, 7, 14, 7),
				PartPose.offset(5, 10, 1));
		return mesh;
	}

	public static LayerDefinition createBodyLayer() {
		return LayerDefinition.create(createMesh(), 128, 64);
	}

	/** Brute rig plus bony spines along the back and a lump on one shoulder. */
	public static LayerDefinition createMutantLayer() {
		MeshDefinition mesh = createMesh();
		PartDefinition body = mesh.getRoot().getChild("body");
		body.addOrReplaceChild("spines", CubeListBuilder.create()
				.texOffs(96, 26).addBox(-1, -6, -1, 2, 6, 2)
				.texOffs(96, 26).addBox(-5, -4, 3, 2, 5, 2)
				.texOffs(96, 26).addBox(3, -5, 2, 2, 6, 2)
				.texOffs(104, 26).addBox(-1, -3, 7, 2, 4, 2),
				PartPose.offset(0, -16, 4));
		body.getChild("right_arm").addOrReplaceChild("growth", CubeListBuilder.create().texOffs(96, 36).addBox(-4, -4, -4, 7, 6, 7),
				PartPose.offsetAndRotation(-2, -5, 1, 0.3f, 0.4f, -0.2f));
		return LayerDefinition.create(mesh, 128, 64);
	}

	/** 0..1 of the overhead windup (slam, leap or throw), 0 when not winding up. */
	protected float windup(MobRenderState state) {
		return 0;
	}

	@Override
	public void setupAnim(MobRenderState state) {
		super.setupAnim(state);
		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.6f;
		head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5f;
		rightLeg.xRot = Mth.cos(pos * 0.5f) * 0.9f * speed;
		leftLeg.xRot = Mth.cos(pos * 0.5f + Mth.PI) * 0.9f * speed;
		body.zRot = Mth.cos(pos * 0.5f) * 0.08f * speed;
		// Knuckle-walk: arms swing low, opposite the legs.
		rightArm.xRot = -0.3f + Mth.cos(pos * 0.5f + Mth.PI) * 0.7f * speed;
		leftArm.xRot = -0.3f + Mth.cos(pos * 0.5f) * 0.7f * speed;
		rightArm.zRot = 0.15f;
		leftArm.zRot = -0.15f;
		// Breathing.
		body.xRot += Mth.sin(state.ageInTicks * 0.08f) * 0.03f;

		float windup = windup(state);
		if (windup > 0) {
			// Both fists rise overhead and the body rears back, telegraphing the slam.
			rightArm.xRot = Mth.lerp(windup, rightArm.xRot, -2.9f);
			leftArm.xRot = Mth.lerp(windup, leftArm.xRot, -2.9f);
			rightArm.zRot = Mth.lerp(windup, 0.15f, -0.3f);
			leftArm.zRot = Mth.lerp(windup, -0.15f, 0.3f);
			body.xRot -= 0.45f * windup;
		} else if (state.swing > 0) {
			float s = Mth.sin(state.swing * Mth.PI);
			rightArm.xRot -= s * 1.8f;
			leftArm.xRot -= s * 1.8f;
			body.xRot += s * 0.2f;
		}
	}
}
