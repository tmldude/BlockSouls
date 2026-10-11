package io.github.tmldude.blocksouls.mobs.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

import io.github.tmldude.blocksouls.mobs.client.render.MobRenderState;

/**
 * Massive humped shoulders and huge knuckle-dragging forearms on short legs, a tiny underbitten head sunk low
 * between the shoulders, a rusted shackle on one wrist. The Mutant Brute uses the same rig with spines and
 * growths added ({@link MobGeometry#mutantBrute()}).
 */
public class BruteModel extends EntityModel<MobRenderState> {
	protected final ModelPart body;
	protected final ModelPart head;
	protected final ModelPart rightArm;
	protected final ModelPart rightForearm;
	protected final ModelPart leftArm;
	protected final ModelPart leftForearm;
	protected final ModelPart rightLeg;
	protected final ModelPart leftLeg;

	public BruteModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		head = body.getChild("head");
		rightArm = body.getChild("right_arm");
		rightForearm = rightArm.getChild("right_forearm");
		leftArm = body.getChild("left_arm");
		leftForearm = leftArm.getChild("left_forearm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		return MobGeometry.brute();
	}

	public static LayerDefinition createMutantLayer() {
		return MobGeometry.mutantBrute();
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
		float t = state.ageInTicks + state.phase;
		float gait = pos * 0.5f;
		head.yRot += state.yRot * Mth.DEG_TO_RAD * 0.6f;
		head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5f;
		// Heavy gait: the body drops onto each step and rolls; the arms knuckle-walk opposite the legs.
		rightLeg.xRot = Mth.cos(gait) * 0.8f * speed;
		leftLeg.xRot = Mth.cos(gait + Mth.PI) * 0.8f * speed;
		body.zRot += Mth.cos(gait) * 0.08f * speed;
		body.y += Math.abs(Mth.cos(gait)) * 1.2f * speed;
		rightArm.xRot += Mth.cos(gait + Mth.PI) * 0.6f * speed;
		leftArm.xRot += Mth.cos(gait) * 0.6f * speed;
		rightForearm.xRot += Math.max(0, Mth.sin(gait + Mth.PI)) * 0.3f * speed;
		leftForearm.xRot += Math.max(0, Mth.sin(gait)) * 0.3f * speed;
		// Breathing: the hump heaves, the arms sway with it.
		float breath = Mth.sin(t * 0.08f);
		body.xRot += breath * 0.03f;
		rightArm.zRot += breath * 0.03f;
		leftArm.zRot -= breath * 0.03f;
		// A snort now and then.
		head.xRot -= Mth.clamp(Mth.sin(t * 0.07f) * 6 - 5, 0, 1) * 0.25f;

		float windup = windup(state);
		if (windup > 0) {
			// Both fists rise high overhead and the body rears back, legs planted wide: readable from far off.
			float shake = Mth.sin(state.ageInTicks * 2.2f) * 0.05f * windup * windup;
			rightArm.xRot = Mth.lerp(windup, rightArm.xRot, -3.2f) + shake;
			leftArm.xRot = Mth.lerp(windup, leftArm.xRot, -3.2f) - shake;
			rightArm.zRot = Mth.lerp(windup, rightArm.zRot, -0.35f);
			leftArm.zRot = Mth.lerp(windup, leftArm.zRot, 0.35f);
			rightForearm.xRot = Mth.lerp(windup, rightForearm.xRot, -0.25f);
			leftForearm.xRot = Mth.lerp(windup, leftForearm.xRot, -0.25f);
			body.xRot -= 0.5f * windup;
			head.xRot += 0.35f * windup;
			rightLeg.zRot += 0.15f * windup;
			leftLeg.zRot -= 0.15f * windup;
		} else if (state.swing > 0) {
			float s = Mth.sin(state.swing * Mth.PI);
			rightArm.xRot -= s * 1.8f;
			leftArm.xRot -= s * 1.8f;
			body.xRot += s * 0.2f;
		}
	}
}
