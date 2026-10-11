package io.github.tmldude.blocksouls.mobs.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

import io.github.tmldude.blocksouls.mobs.client.render.MobRenderState;

/**
 * Hunched corpse in a torn work shirt: head lolling, jaw hanging, the long right arm dragging its knuckles along
 * the ground, the withered left arm reaching for you. Limps on its bare left leg.
 */
public class ShamblerModel extends EntityModel<MobRenderState> {
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart rightArm;
	private final ModelPart rightForearm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public ShamblerModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		head = body.getChild("head");
		jaw = head.getChild("jaw");
		rightArm = body.getChild("right_arm");
		rightForearm = rightArm.getChild("right_forearm");
		leftArm = body.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		return MobGeometry.shambler();
	}

	@Override
	public void setupAnim(MobRenderState state) {
		super.setupAnim(state);
		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		float t = state.ageInTicks + state.phase;
		head.yRot += state.yRot * Mth.DEG_TO_RAD * 0.7f;
		head.xRot += Mth.clamp(state.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.5f;
		// Every few seconds the head jerks, like something snapped in the neck.
		float jerk = Mth.clamp(Mth.sin(t * 0.11f) * 5 - 4, 0, 1);
		head.zRot += Mth.sin(t * 1.7f) * 0.2f * jerk;
		jaw.xRot += Mth.sin(t * 0.09f) * 0.1f;

		// A limping lurch: full stride on the clothed leg, a short stiff one on the bare one, body rolling.
		float gait = pos * 0.6662f;
		rightLeg.xRot = Mth.cos(gait) * 1.1f * speed;
		leftLeg.xRot = Mth.cos(gait + Mth.PI) * 0.6f * speed;
		body.zRot += Mth.cos(gait) * 0.14f * speed;
		body.xRot += Math.abs(Mth.sin(gait)) * 0.08f * speed;
		body.y += Math.abs(Mth.cos(gait)) * 0.8f * speed;

		// The dragging arm trails behind the faster it moves; the reaching arm lifts once it has a target.
		rightArm.xRot += speed * 0.3f + Mth.cos(gait + Mth.PI) * 0.12f * speed;
		rightArm.zRot += Mth.sin(t * 0.05f) * 0.03f;
		leftArm.xRot += (state.aggressive ? -0.4f : 0) + Mth.sin(t * 0.067f) * 0.08f + Mth.cos(gait) * 0.2f * speed;
		leftArm.zRot += Mth.cos(t * 0.09f) * 0.05f;

		if (state.swing > 0) {
			// The long arm comes up and over like a club.
			float s = Mth.sin(state.swing * Mth.PI);
			rightArm.xRot -= s * 2.3f;
			rightForearm.xRot -= s * 0.5f;
			leftArm.xRot -= s * 0.3f;
			body.xRot -= s * 0.2f;
			jaw.xRot += s * 0.4f;
		}
	}
}
