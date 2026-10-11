package io.github.tmldude.blocksouls.mobs.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

import io.github.tmldude.blocksouls.mobs.client.render.MobRenderState;
import io.github.tmldude.blocksouls.mobs.entity.Screamer;

/**
 * Gaunt and tall: wasp waist, open ribcage, long jointed arms ending in two long fingers, and a distended jaw that
 * hangs slack, then drops wide open when it screams.
 */
public class ScreamerModel extends EntityModel<MobRenderState> {
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart rightArm;
	private final ModelPart rightForearm;
	private final ModelPart rightHand;
	private final ModelPart leftArm;
	private final ModelPart leftForearm;
	private final ModelPart leftHand;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public ScreamerModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		head = body.getChild("head");
		jaw = head.getChild("jaw");
		rightArm = body.getChild("right_arm");
		rightForearm = rightArm.getChild("right_forearm");
		rightHand = rightForearm.getChild("right_hand");
		leftArm = body.getChild("left_arm");
		leftForearm = leftArm.getChild("left_forearm");
		leftHand = leftForearm.getChild("left_hand");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		return MobGeometry.screamer();
	}

	@Override
	public void setupAnim(MobRenderState state) {
		super.setupAnim(state);
		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		float t = state.ageInTicks + state.phase;
		float gait = pos * 0.6662f;
		head.yRot += state.yRot * Mth.DEG_TO_RAD;
		head.xRot += state.xRot * Mth.DEG_TO_RAD;
		// Long stiff strides; the forearms flop a beat behind the upper arms.
		rightLeg.xRot = Mth.cos(gait) * 1.1f * speed;
		leftLeg.xRot = Mth.cos(gait + Mth.PI) * 1.1f * speed;
		rightArm.xRot += Mth.cos(gait + Mth.PI) * 0.5f * speed;
		leftArm.xRot += Mth.cos(gait) * 0.5f * speed;
		rightForearm.xRot -= Math.max(0, Mth.cos(gait + Mth.PI - 0.8f)) * 0.5f * speed;
		leftForearm.xRot -= Math.max(0, Mth.cos(gait - 0.8f)) * 0.5f * speed;
		// Idle: head twitches, fingers flex, jaw sways loose.
		head.zRot += Mth.sin(t * 0.31f) * Mth.sin(t * 0.07f) * 0.12f;
		rightHand.xRot += Mth.sin(t * 0.23f) * 0.15f;
		leftHand.xRot += Mth.sin(t * 0.19f + 1) * 0.15f;
		jaw.xRot += Mth.sin(t * 0.2f) * 0.06f;
		if (state.aggressive) {
			body.xRot += 0.12f;
			head.xRot -= 0.12f;
		}

		if (state.action == Screamer.ACTION_SCREAM) {
			// Jaw drops wide, head throws back, arms flare out with the hands clawed, the whole body shakes.
			float k = Mth.clamp(state.actionTicks / 4f, 0, 1) * Mth.clamp((Screamer.SCREAM_TICKS - state.actionTicks) / 4f, 0, 1);
			jaw.xRot += 1.25f * k;
			head.xRot -= 0.35f * k;
			head.yRot += Mth.sin(state.ageInTicks * 2.5f) * 0.08f * k;
			body.xRot -= 0.25f * k;
			body.zRot += Mth.sin(state.ageInTicks * 3.1f) * 0.04f * k;
			rightArm.zRot += 0.75f * k;
			leftArm.zRot -= 0.75f * k;
			rightArm.xRot -= 0.3f * k;
			leftArm.xRot -= 0.3f * k;
			rightForearm.xRot -= 0.9f * k;
			leftForearm.xRot -= 0.9f * k;
			rightHand.xRot -= 0.6f * k;
			leftHand.xRot -= 0.6f * k;
		} else if (state.swing > 0) {
			float s = Mth.sin(state.swing * Mth.PI);
			rightArm.xRot -= s * 1.6f;
			rightForearm.xRot -= s * 0.6f;
		}
	}
}
