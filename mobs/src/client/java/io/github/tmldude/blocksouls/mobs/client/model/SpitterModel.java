package io.github.tmldude.blocksouls.mobs.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.client.render.MobRenderState;
import io.github.tmldude.blocksouls.mobs.entity.Spitter;

/**
 * Stooped, bile-sodden corpse with a throat sac under its jaw. The tell: it plants its feet, throws its head
 * back and the sac swells until the glob flies.
 */
public class SpitterModel extends EntityModel<MobRenderState> {
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart throat;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public SpitterModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		head = body.getChild("head");
		jaw = head.getChild("jaw");
		throat = head.getChild("throat");
		rightArm = body.getChild("right_arm");
		leftArm = body.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		return MobGeometry.spitter();
	}

	@Override
	public void setupAnim(MobRenderState state) {
		super.setupAnim(state);
		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		float t = state.ageInTicks + state.phase;
		head.yRot += state.yRot * Mth.DEG_TO_RAD * 0.8f;
		head.xRot += Mth.clamp(state.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.6f;
		// The sac breathes even at rest.
		float breathe = 1 + Mth.sin(t * 0.12f) * 0.06f;
		throat.xScale = breathe;
		throat.yScale = breathe;
		throat.zScale = breathe;

		float gait = pos * 0.6662f;
		rightLeg.xRot = Mth.cos(gait) * 1.0f * speed;
		leftLeg.xRot = Mth.cos(gait + Mth.PI) * 1.0f * speed;
		rightArm.xRot += Mth.cos(gait + Mth.PI) * 0.4f * speed + Mth.sin(t * 0.07f) * 0.05f;
		leftArm.xRot += Mth.cos(gait) * 0.4f * speed - Mth.sin(t * 0.07f) * 0.05f;

		if (state.action == Spitter.ACTION_SPIT) {
			float k = Mth.clamp(state.actionTicks / BlockSoulsMobs.CONFIG.spitter.spitWindupTicks, 0, 1);
			head.xRot -= k * 0.5f;
			jaw.xRot += k * 0.7f;
			body.xRot -= k * 0.15f;
			float swell = 1 + k * 0.45f + Mth.sin(state.actionTicks * 1.3f) * 0.04f * k;
			throat.xScale = swell;
			throat.yScale = swell + k * 0.05f;
			throat.zScale = swell;
			rightArm.xRot += k * 0.3f;
			rightArm.zRot += k * 0.3f;
			leftArm.xRot += k * 0.3f;
			leftArm.zRot -= k * 0.3f;
		}
		if (state.swing > 0) {
			float s = Mth.sin(state.swing * Mth.PI);
			rightArm.xRot -= s * 1.8f;
			body.xRot += s * 0.15f;
		}
	}
}
