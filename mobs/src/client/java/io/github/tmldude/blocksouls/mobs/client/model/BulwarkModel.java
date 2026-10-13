package io.github.tmldude.blocksouls.mobs.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.client.render.MobRenderState;
import io.github.tmldude.blocksouls.mobs.entity.Bulwark;

/**
 * A hulk behind a slab of riveted scrap. Plods, shield always up. The tell: it draws the shield back and twists
 * onto its back foot; the bash itself (the attack swing) slams the slab forward.
 */
public class BulwarkModel extends EntityModel<MobRenderState> {
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart shield;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public BulwarkModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		head = body.getChild("head");
		shield = body.getChild("shield");
		rightArm = body.getChild("right_arm");
		leftArm = body.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		return MobGeometry.bulwark();
	}

	@Override
	public void setupAnim(MobRenderState state) {
		super.setupAnim(state);
		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		float t = state.ageInTicks + state.phase;
		head.yRot += state.yRot * Mth.DEG_TO_RAD * 0.4f;
		head.xRot += Mth.clamp(state.xRot, -20, 20) * Mth.DEG_TO_RAD * 0.4f;

		// A heavy plod: short strides, the body (and shield) rocking with each step.
		float gait = pos * 0.5f;
		rightLeg.xRot = Mth.cos(gait) * 0.8f * speed;
		leftLeg.xRot = Mth.cos(gait + Mth.PI) * 0.8f * speed;
		body.zRot += Mth.cos(gait) * 0.05f * speed;
		body.y += Math.abs(Mth.cos(gait)) * 0.6f * speed;
		rightArm.xRot += Mth.cos(gait + Mth.PI) * 0.35f * speed + Mth.sin(t * 0.06f) * 0.04f;
		shield.y += Mth.sin(t * 0.08f) * 0.2f;

		if (state.action == Bulwark.ACTION_BASH) {
			float k = Mth.clamp(state.actionTicks / BlockSoulsMobs.CONFIG.bulwark.bashWindupTicks, 0, 1);
			shield.z += k * 3;
			shield.xRot += k * 0.15f;
			shield.yRot += k * 0.35f;
			leftArm.xRot += k * 0.4f;
			body.xRot -= k * 0.12f;
			body.yRot += k * 0.25f;
			rightLeg.xRot += k * 0.4f;
			leftLeg.xRot -= k * 0.3f;
		}
		if (state.swing > 0) {
			// The slab slams forward.
			float s = Mth.sin(state.swing * Mth.PI);
			shield.z -= s * 5;
			shield.xRot -= s * 0.2f;
			leftArm.xRot -= s * 0.3f;
			body.xRot += s * 0.15f;
		}
	}
}
