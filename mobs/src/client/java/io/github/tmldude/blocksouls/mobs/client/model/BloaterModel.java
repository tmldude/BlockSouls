package io.github.tmldude.blocksouls.mobs.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.client.render.MobRenderState;
import io.github.tmldude.blocksouls.mobs.entity.Bloater;

/**
 * A swollen sack of a body on stubby legs, a tiny head sunk into the fat, glowing pustules that throb out of step.
 * During its telegraph it inflates, the pustules bulge and the whole body shudders faster until it bursts.
 */
public class BloaterModel extends EntityModel<MobRenderState> {
	private static final String[] SACS = {"sac_front", "sac_low", "sac_right", "sac_left", "sac_top", "sac_back"};

	private final ModelPart belly;
	private final ModelPart[] sacs = new ModelPart[SACS.length];
	private final ModelPart head;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	private final ModelPart rightArm;
	private final ModelPart leftArm;

	public BloaterModel(ModelPart root) {
		super(root);
		belly = root.getChild("belly");
		for (int i = 0; i < SACS.length; i++) {
			sacs[i] = belly.getChild(SACS[i]);
		}
		head = root.getChild("head");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
		rightArm = root.getChild("right_arm");
		leftArm = root.getChild("left_arm");
	}

	public static LayerDefinition createBodyLayer() {
		return MobGeometry.bloater();
	}

	@Override
	public void setupAnim(MobRenderState state) {
		super.setupAnim(state);
		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		float t = state.ageInTicks + state.phase;
		head.yRot += state.yRot * Mth.DEG_TO_RAD * 0.5f;
		head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5f;
		// Waddle: short steps, the whole sack rocking side to side and bouncing.
		float gait = pos * 0.9f;
		rightLeg.xRot = Mth.cos(gait) * 1.0f * speed;
		leftLeg.xRot = Mth.cos(gait + Mth.PI) * 1.0f * speed;
		belly.zRot += Mth.cos(gait) * 0.09f * speed;
		belly.y += Math.abs(Mth.sin(gait)) * 0.8f * speed;
		head.zRot += Mth.cos(gait) * 0.12f * speed;
		rightArm.xRot += Mth.cos(gait + Mth.PI) * 0.4f * speed;
		leftArm.xRot += Mth.cos(gait) * 0.4f * speed;
		rightArm.zRot += Mth.sin(t * 0.1f) * 0.05f;
		leftArm.zRot -= Mth.sin(t * 0.1f) * 0.05f;

		float pulse = 1 + Mth.sin(t * 0.12f) * 0.03f;
		float swell = 0;
		float throb = 0.08f;
		float rate = 0.15f;
		if (state.action == Bloater.ACTION_SWELL) {
			swell = Mth.clamp(state.actionTicks / BlockSoulsMobs.CONFIG.bloater.swellTicks, 0, 1);
			// Shudders faster and harder as it nears bursting.
			pulse += Mth.sin(state.ageInTicks * (1.2f + swell * 2f)) * 0.05f * swell;
			belly.zRot += Mth.sin(state.ageInTicks * (1.5f + swell * 2.5f)) * 0.05f * swell;
			rightArm.zRot += swell * 0.8f;
			leftArm.zRot -= swell * 0.8f;
			head.xRot -= swell * 0.4f;
			throb += swell * 0.12f;
			rate += swell * 0.6f;
		}
		belly.xScale = pulse + swell * 0.32f;
		belly.yScale = pulse + swell * 0.22f;
		belly.zScale = pulse + swell * 0.32f;
		head.y -= swell * 3f;
		for (int i = 0; i < sacs.length; i++) {
			float s = 1 + Mth.sin(t * rate + i * 1.7f) * throb + swell * 0.3f;
			sacs[i].xScale = s;
			sacs[i].yScale = s;
			sacs[i].zScale = s;
		}
	}
}
