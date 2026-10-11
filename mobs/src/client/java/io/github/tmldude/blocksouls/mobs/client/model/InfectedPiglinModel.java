package io.github.tmldude.blocksouls.mobs.client.model;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;

/**
 * Piglin proportions (wide snouted head, floppy ear, tusks) overgrown with fleshy blight: a growth swallowing the
 * left eye, the left ear fused into a lump, tumors on the scalp, back, knee and a swollen left arm. Humanoid rig
 * so it can hold its gold sword.
 */
public class InfectedPiglinModel extends HumanoidModel<HumanoidRenderState> {
	private final ModelPart rightEar;
	private final ModelPart leftEar;
	private final ModelPart headGrowth;
	private final ModelPart backGrowth;

	public InfectedPiglinModel(ModelPart root) {
		super(root);
		rightEar = head.getChild("right_ear");
		leftEar = head.getChild("left_ear");
		headGrowth = head.getChild("head_growth");
		backGrowth = body.getChild("back_growth");
	}

	public static LayerDefinition createBodyLayer() {
		return MobGeometry.infectedPiglin();
	}

	@Override
	public void setupAnim(HumanoidRenderState state) {
		super.setupAnim(state);
		float t = state.ageInTicks * 0.1f + state.walkAnimationPos * 0.5f;
		float amp = 0.08f + state.walkAnimationSpeed * 0.4f;
		rightEar.zRot += Mth.cos(t) * amp;
		// The fused ear barely moves; it throbs instead, like the other growths.
		float throb = 1 + Mth.sin(state.ageInTicks * 0.15f) * 0.06f;
		leftEar.xScale = throb;
		leftEar.yScale = throb;
		leftEar.zScale = throb;
		headGrowth.xScale = throb;
		headGrowth.zScale = throb;
		float backThrob = 1 + Mth.sin(state.ageInTicks * 0.15f + 2) * 0.06f;
		backGrowth.xScale = backThrob;
		backGrowth.yScale = backThrob;
		// Twitchy, sick head, tilted toward the heavy side.
		head.zRot += 0.08f + Mth.sin(state.ageInTicks * 0.43f) * Mth.sin(state.ageInTicks * 0.11f) * 0.1f;
	}

	@Override
	protected void setupAttackAnimation(HumanoidRenderState state) {
		super.setupAttackAnimation(state);
		// Off arm claws forward like the infected do.
		AnimationUtils.bobModelPart(leftArm, state.ageInTicks, -1);
		leftArm.xRot -= 0.9f;
	}
}
