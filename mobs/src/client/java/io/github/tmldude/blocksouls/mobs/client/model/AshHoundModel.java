package io.github.tmldude.blocksouls.mobs.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

import io.github.tmldude.blocksouls.mobs.client.render.MobRenderState;

/**
 * Lean, deep-chested hunter of charred fur and peeling ash flakes, digitigrade hind legs, a ragged tail. Ember eyes,
 * mouth and cracks glow (emissive layer). Snarls when hunting, lunges jaws-first mid-pounce.
 */
public class AshHoundModel extends EntityModel<MobRenderState> {
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart tail;
	private final ModelPart tailTip;
	private final ModelPart rightFrontLeg;
	private final ModelPart leftFrontLeg;
	private final ModelPart rightHindLeg;
	private final ModelPart leftHindLeg;
	private final ModelPart rightHindShin;
	private final ModelPart leftHindShin;

	public AshHoundModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		head = body.getChild("head");
		jaw = head.getChild("jaw");
		tail = body.getChild("tail");
		tailTip = tail.getChild("tail_tip");
		rightFrontLeg = root.getChild("right_front_leg");
		leftFrontLeg = root.getChild("left_front_leg");
		rightHindLeg = root.getChild("right_hind_leg");
		leftHindLeg = root.getChild("left_hind_leg");
		rightHindShin = rightHindLeg.getChild("right_hind_shin");
		leftHindShin = leftHindLeg.getChild("left_hind_shin");
	}

	public static LayerDefinition createBodyLayer() {
		return MobGeometry.ashHound();
	}

	@Override
	public void setupAnim(MobRenderState state) {
		super.setupAnim(state);
		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		float t = state.ageInTicks + state.phase;
		head.yRot += state.yRot * Mth.DEG_TO_RAD * 0.8f;
		head.xRot += state.xRot * Mth.DEG_TO_RAD;
		// A gallop: front pair and hind pair each nearly in phase, the hind shins folding on the way forward.
		float gait = pos * 0.9f;
		rightFrontLeg.xRot = Mth.cos(gait) * 1.1f * speed;
		leftFrontLeg.xRot = Mth.cos(gait + 0.4f) * 1.1f * speed;
		rightHindLeg.xRot += -Mth.cos(gait) * 0.8f * speed;
		leftHindLeg.xRot += -Mth.cos(gait + 0.4f) * 0.8f * speed;
		rightHindShin.xRot += Math.max(0, Mth.sin(gait)) * 0.6f * speed;
		leftHindShin.xRot += Math.max(0, Mth.sin(gait + 0.4f)) * 0.6f * speed;
		body.xRot += Mth.sin(gait) * 0.08f * speed;
		head.xRot -= Mth.sin(gait) * 0.08f * speed;
		// Tail hangs and sways at rest, streams out behind at a run.
		tail.xRot += speed * 0.5f;
		tail.yRot += Mth.sin(t * 0.25f) * 0.25f * (1 - speed);
		tailTip.xRot += Mth.sin(t * 0.25f + 1) * 0.15f;
		// Panting, or a low snarl with the head dropped when it has prey.
		if (state.aggressive) {
			jaw.xRot += 0.2f + Mth.sin(t * 0.9f) * 0.05f;
			head.xRot += 0.15f;
		} else {
			jaw.xRot += Math.abs(Mth.sin(t * 0.35f)) * 0.15f;
		}

		if (state.airborne) {
			// Mid-pounce: forelegs reaching, hind legs stretched back, jaws wide.
			rightFrontLeg.xRot = -1.1f;
			leftFrontLeg.xRot = -1.0f;
			rightHindLeg.xRot += 0.6f;
			leftHindLeg.xRot += 0.7f;
			body.xRot -= 0.2f;
			head.xRot -= 0.2f;
			jaw.xRot += 0.6f;
			tail.xRot -= 0.3f;
		}
		if (state.swing > 0) {
			float s = Mth.sin(state.swing * Mth.PI);
			head.xRot += s * 0.4f;
			jaw.xRot += s * 0.6f;
		}
	}
}
