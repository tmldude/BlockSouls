package io.github.tmldude.blocksouls.mobs.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

import io.github.tmldude.blocksouls.mobs.client.render.MobRenderState;

/**
 * Low, split-backed torso on six jointed limbs with the elbows high, like a spider's. Scuttles on alternating
 * tripods, chatters its jaw, and splays its front limbs forward mid-pounce.
 */
public class CrawlerModel extends EntityModel<MobRenderState> {
	private static final String[] LIMBS = {"right_front", "left_mid", "right_hind", "left_front", "right_mid", "left_hind"};

	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart jaw;
	/** Limb roots (yaw), upper and lower segments, tripod A first. */
	private final ModelPart[] roots = new ModelPart[6];
	private final ModelPart[] uppers = new ModelPart[6];
	private final ModelPart[] lowers = new ModelPart[6];

	public CrawlerModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		head = root.getChild("head");
		jaw = head.getChild("jaw");
		for (int i = 0; i < 6; i++) {
			roots[i] = body.getChild(LIMBS[i]);
			uppers[i] = roots[i].getChild(LIMBS[i] + "_upper");
			lowers[i] = uppers[i].getChild(LIMBS[i] + "_lower");
		}
	}

	public static LayerDefinition createBodyLayer() {
		return MobGeometry.crawler();
	}

	@Override
	public void setupAnim(MobRenderState state) {
		super.setupAnim(state);
		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		float t = state.ageInTicks + state.phase;
		head.yRot += Mth.clamp(state.yRot, -50, 50) * Mth.DEG_TO_RAD;
		head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.6f;
		jaw.xRot += Math.abs(Mth.sin(t * 0.6f)) * 0.12f;
		body.y += Mth.sin(pos * 2.6f) * 0.4f * speed;

		for (int i = 0; i < 6; i++) {
			// Right limbs are on -x: a positive yaw swings their feet backward, so the sign flips per side.
			float side = LIMBS[i].startsWith("right") ? -1 : 1;
			float phase = i < 3 ? 0 : Mth.PI;
			float swing = Mth.cos(pos * 1.3f + phase) * 0.4f * speed;
			float lift = Math.max(0, Mth.sin(pos * 1.3f + phase)) * 0.45f * speed;
			roots[i].yRot += side * swing;
			// A smaller tilt brings the elbow, and with it the foot, up.
			uppers[i].zRot -= side * lift;
			// Idle: now and then a limb flexes, tapping the ground.
			float tap = Mth.clamp(Mth.sin(t * 0.21f + i * 2.1f) * 6 - 5, 0, 1);
			lowers[i].zRot += side * tap * 0.25f;
		}

		if (state.airborne) {
			// Mid-pounce: front limbs thrown forward and up, hind limbs trailing, mouth wide.
			for (int i = 0; i < 6; i++) {
				float side = LIMBS[i].startsWith("right") ? -1 : 1;
				if (LIMBS[i].endsWith("front")) {
					roots[i].yRot -= side * 0.4f;
					uppers[i].zRot += side * 0.45f;
					lowers[i].zRot -= side * 0.5f;
				} else if (LIMBS[i].endsWith("hind")) {
					roots[i].yRot += side * 0.35f;
				}
			}
			head.xRot -= 0.35f;
			jaw.xRot += 0.6f;
		}
		if (state.swing > 0) {
			float s = Mth.sin(state.swing * Mth.PI);
			head.xRot -= s * 0.5f;
			head.z -= s * 2;
			jaw.xRot += s * 0.7f;
			body.xRot -= s * 0.15f;
		}
	}
}
