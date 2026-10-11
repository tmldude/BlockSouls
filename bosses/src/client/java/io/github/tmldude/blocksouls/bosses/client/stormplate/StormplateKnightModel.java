package io.github.tmldude.blocksouls.bosses.client.stormplate;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import io.github.tmldude.blocksouls.bosses.api.AttackStats;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.Stage;
import io.github.tmldude.blocksouls.bosses.client.render.Anim;
import io.github.tmldude.blocksouls.bosses.client.render.BossModel;
import io.github.tmldude.blocksouls.bosses.client.render.BossRenderState;
import io.github.tmldude.blocksouls.bosses.stormplate.StormplateConfig;
import io.github.tmldude.blocksouls.bosses.stormplate.StormplateKnight;

/**
 * A tall, narrow-waisted knight in layered scale plate: a crested helm with swept fins, a scaled breastplate over the
 * torso, a plated skirt, heavy pauldrons, a torn cape, and a long spear held low in the right hand (pointing
 * forward). Drawn at 1.15× so it stands 4 blocks tall. Until the fight starts it kneels on one knee with the spear
 * planted. Box UVs are mirrored in {@code tools/assets/stormplate.py}.
 */
public class StormplateKnightModel extends BossModel {
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart cape;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart spear;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public StormplateKnightModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		head = body.getChild("head");
		cape = body.getChild("cape");
		rightArm = body.getChild("right_arm");
		leftArm = body.getChild("left_arm");
		spear = rightArm.getChild("spear");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-8, -20, -5, 16, 20, 10)
				.texOffs(0, 30).addBox(-8.5f, -19, -5.5f, 17, 9, 11)
				.texOffs(0, 50).addBox(-8.5f, -2, -5.5f, 17, 8, 11),
				PartPose.offset(0, 2, 0));
		body.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(56, 0).addBox(-5, -10, -5, 10, 10, 10)
				.texOffs(96, 0).addBox(-1, -14, -4, 2, 5, 10)
				.texOffs(96, 16).addBox(-7, -11, 0, 2, 6, 4)
				.texOffs(96, 16).mirror().addBox(5, -11, 0, 2, 6, 4),
				PartPose.offset(0, -20, 0));
		body.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(56, 20).addBox(-7, 0, 0, 14, 28, 1),
				PartPose.offset(0, -19, 5.5f));
		PartDefinition rightArm = body.addOrReplaceChild("right_arm", CubeListBuilder.create()
				.texOffs(0, 70).addBox(-3, -2, -3, 6, 20, 6)
				.texOffs(24, 70).addBox(-5, -4, -4.5f, 8, 6, 9),
				PartPose.offset(-10.5f, -18, 0));
		rightArm.addOrReplaceChild("spear", CubeListBuilder.create()
				.texOffs(0, 96).addBox(-0.5f, -0.5f, -34, 1, 1, 26)
				.texOffs(0, 96).addBox(-0.5f, -0.5f, -8, 1, 1, 26)
				.texOffs(76, 85).addBox(-1, -3, -35, 2, 6, 1)
				.texOffs(58, 85).addBox(-0.5f, -2, -43, 1, 4, 8),
				PartPose.offset(0, 17, 0));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create()
				.texOffs(0, 70).mirror().addBox(-3, -2, -3, 6, 20, 6)
				.texOffs(24, 70).mirror().addBox(-3, -4, -4.5f, 8, 6, 9),
				PartPose.offset(10.5f, -18, 0));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(86, 26).addBox(-3, 0, -3.5f, 6, 22, 7),
				PartPose.offset(-4, 2, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(86, 26).mirror().addBox(-3, 0, -3.5f, 6, 22, 7),
				PartPose.offset(4, 2, 0));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(BossRenderState state) {
		super.setupAnim(state);
		// The thrown spear flies as particles; the hand is empty until it comes back.
		spear.visible = !("throw".equals(state.attack) && state.stage == Stage.ACTIVE);
	}

	@Override
	protected void idle(BossRenderState state) {
		if (!state.engaged && state.stage == Stage.NONE) {
			kneel(state);
			return;
		}
		float pos = state.walkAnimationPos;
		float speed = Math.min(1f, state.walkAnimationSpeed);
		rightLeg.xRot = Mth.cos(pos * 0.45f) * 0.7f * speed;
		leftLeg.xRot = Mth.cos(pos * 0.45f + Mth.PI) * 0.7f * speed;
		leftArm.xRot = Mth.cos(pos * 0.45f) * 0.35f * speed;
		// The spear rides low and forward, ready to thrust.
		rightArm.xRot = -0.3f + Mth.cos(pos * 0.45f + Mth.PI) * 0.12f * speed;
		rightArm.zRot = 0.06f;
		leftArm.zRot = -0.1f;
		body.zRot = Mth.cos(pos * 0.45f) * 0.04f * speed;
		body.xRot = 0.04f + Mth.sin(state.ageInTicks * 0.05f) * 0.02f;
		cape.xRot = 0.12f + speed * 0.55f + Mth.sin(state.ageInTicks * 0.09f) * 0.06f;
		cape.zRot = Mth.sin(state.ageInTicks * 0.07f) * 0.03f;
		head.yRot = Mth.clamp(state.yRot, -40, 40) * Mth.DEG_TO_RAD * 0.5f;
		head.xRot = Mth.clamp(state.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.4f;
	}

	/** Down on the left knee, head bowed, both hands on the planted spear. */
	private void kneel(BossRenderState state) {
		float breathe = Mth.sin(state.ageInTicks * 0.04f) * 0.02f;
		body.y += 10;
		rightLeg.y += 10;
		leftLeg.y += 10;
		rightLeg.xRot = -1.05f;
		leftLeg.xRot = 1.2f;
		leftLeg.z += 2;
		body.xRot = 0.3f + breathe;
		head.xRot = 0.45f;
		rightArm.xRot = -0.95f;
		rightArm.zRot = 0.15f;
		leftArm.xRot = -0.9f;
		leftArm.yRot = -0.5f;
		leftArm.zRot = -0.1f;
		// The spear held upright, butt planted beside the knee.
		spear.xRot = -0.65f;
		cape.xRot = 0.55f;
	}

	private static float seconds(int ticks) {
		return Math.max(0.05f, ticks / 20f);
	}

	private static StormplateConfig config() {
		return StormplateKnight.config();
	}

	@Override
	protected @Nullable AnimationDefinition animation(@Nullable String attack, Stage stage) {
		if (attack == null) {
			return stage == Stage.STAGGER ? roar(seconds(BossEntity.framework().staggerTicks)) : null;
		}
		return switch (attack) {
			case "thrust" -> thrust(config().thrust, stage);
			case "sweep" -> sweep(config().sweep, stage);
			case "thunder" -> thunder(config().thunder, stage);
			case "throw" -> spearThrow(config().spearThrow, stage);
			case "leap" -> leap(config().leap, stage);
			default -> null;
		};
	}

	/** Draws the spear back with a twist of the hips, then drives it forward with the whole body. */
	private static @Nullable AnimationDefinition thrust(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("body", 0, 0, 0, 0, w, 0, 28, 0)
					.rot("right_arm", 0, 0, 0, 0, w, -10, 0, 0)
					.pos("right_arm", 0, 0, 0, 0, w, 0, -1, 9)
					.rot("left_arm", 0, 0, 0, 0, w, -55, -20, 0)
					.rot("left_leg", 0, 0, 0, 0, w, -20, 0, 0)
					.rot("right_leg", 0, 0, 0, 0, w, 15, 0, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("body", 0, 0, 28, 0, a * 0.5f, 14, -14, 0, a, 14, -14, 0)
					.rot("right_arm", 0, -10, 0, 0, a * 0.5f, -14, 0, 0, a, -14, 0, 0)
					.pos("right_arm", 0, 0, -1, 9, a * 0.5f, 0, -1, -12, a, 0, -1, -12)
					.rot("left_arm", 0, -55, -20, 0, a, 20, 0, -20)
					.rot("left_leg", 0, -20, 0, 0, a, -35, 0, 0)
					.rot("right_leg", 0, 15, 0, 0, a, 25, 0, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("body", 0, 14, -14, 0, r * 0.5f, 14, -14, 0, r, 0, 0, 0)
					.rot("right_arm", 0, -14, 0, 0, r, 0, 0, 0)
					.pos("right_arm", 0, 0, -1, -12, r * 0.5f, 0, -1, -12, r, 0, 0, 0)
					.rot("left_arm", 0, 20, 0, -20, r, 0, 0, 0)
					.rot("left_leg", 0, -35, 0, 0, r, 0, 0, 0)
					.rot("right_leg", 0, 25, 0, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Winds far to the right with the shaft held low, then sweeps it around in a wide arc. */
	private static @Nullable AnimationDefinition sweep(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("body", 0, 0, 0, 0, w, 12, 55, 0)
					.pos("body", 0, 0, 0, 0, w, 0, 3, 0)
					.rot("right_arm", 0, 0, 0, 0, w, -35, 60, 25)
					.rot("left_arm", 0, 0, 0, 0, w, -50, 30, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("body", 0, 12, 55, 0, a, 12, -75, 0)
					.pos("body", 0, 0, 3, 0, a, 0, 3, 0)
					.rot("right_arm", 0, -35, 60, 25, a, -40, -85, 25)
					.rot("left_arm", 0, -50, 30, 0, a, -30, -10, -40)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("body", 0, 12, -75, 0, r * 0.5f, 12, -75, 0, r, 0, 0, 0)
					.pos("body", 0, 0, 3, 0, r, 0, 0, 0)
					.rot("right_arm", 0, -40, -85, 25, r * 0.5f, -40, -85, 25, r, 0, 0, 0)
					.rot("left_arm", 0, -30, -10, -40, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Raises the spear straight up to the sky, head thrown back; brings it down as the lightning falls. */
	private static @Nullable AnimationDefinition thunder(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("right_arm", 0, 0, 0, 0, w * 0.4f, -170, 0, 5, w, -172, 0, 5)
					.rot("spear", 0, 0, 0, 0, w * 0.4f, 90, 0, 0, w, 90, 0, 0)
					.rot("left_arm", 0, 0, 0, 0, w * 0.4f, -20, 0, -45, w, -20, 0, -45)
					.rot("head", 0, 0, 0, 0, w * 0.4f, -30, 0, 0, w, -35, 0, 0)
					.rot("body", 0, 0, 0, 0, w * 0.4f, -10, 0, 0, w, -12, 0, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("right_arm", 0, -172, 0, 5, a, -172, 0, 5)
					.rot("spear", 0, 90, 0, 0, a, 90, 0, 0)
					.rot("left_arm", 0, -20, 0, -45, a, -20, 0, -45)
					.rot("head", 0, -35, 0, 0, a, -35, 0, 0)
					.rot("body", 0, -12, 0, 0, a, -12, 0, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("right_arm", 0, -172, 0, 5, r * 0.3f, -172, 0, 5, r, 0, 0, 0)
					.rot("spear", 0, 90, 0, 0, r * 0.3f, 90, 0, 0, r, 0, 0, 0)
					.rot("left_arm", 0, -20, 0, -45, r, 0, 0, 0)
					.rot("head", 0, -35, 0, 0, r, 0, 0, 0)
					.rot("body", 0, -12, 0, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Cocks the spear over its shoulder like a javelin, hurls it, then holds out an open hand to call it back. */
	private static @Nullable AnimationDefinition spearThrow(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("right_arm", 0, 0, 0, 0, w * 0.7f, -155, 0, 10, w, -160, 0, 10)
					.rot("spear", 0, 0, 0, 0, w * 0.7f, 155, 0, 0, w, 160, 0, 0)
					.rot("body", 0, 0, 0, 0, w, -8, 30, 0)
					.rot("left_arm", 0, 0, 0, 0, w, -80, -25, 0)
					.rot("left_leg", 0, 0, 0, 0, w, -25, 0, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("right_arm", 0, -160, 0, 10, a * 0.15f, -40, 0, 10, a * 0.6f, -85, 0, 10, a, -85, 0, 10)
					.rot("body", 0, -8, 30, 0, a * 0.15f, 15, -20, 0, a * 0.6f, 5, 0, 0, a, 5, 0, 0)
					.rot("left_arm", 0, -80, -25, 0, a * 0.15f, 10, 0, -15, a, 10, 0, -15)
					.rot("left_leg", 0, -25, 0, 0, a * 0.15f, -10, 0, 0, a, 0, 0, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("right_arm", 0, -85, 0, 10, r * 0.3f, -60, 0, 10, r, 0, 0, 0)
					.rot("body", 0, 5, 0, 0, r, 0, 0, 0)
					.rot("left_arm", 0, 10, 0, -15, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Crouches low, springs up with the spear overhead, and drives it point-down into the landing. */
	private static @Nullable AnimationDefinition leap(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.pos("body", 0, 0, 0, 0, w * 0.8f, 0, 5, 0, w, 0, 5, 0)
					.rot("body", 0, 0, 0, 0, w * 0.8f, 25, 0, 0, w, 25, 0, 0)
					.rot("right_arm", 0, 0, 0, 0, w, 30, 0, 15)
					.rot("left_arm", 0, 0, 0, 0, w, 30, 0, -15)
					.rot("right_leg", 0, 0, 0, 0, w, -40, 0, 0)
					.rot("left_leg", 0, 0, 0, 0, w, -40, 0, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.pos("body", 0, 0, 5, 0, a * 0.3f, 0, 0, 0, a, 0, 2, 0)
					.rot("body", 0, 25, 0, 0, a * 0.3f, -15, 0, 0, a * 0.85f, -10, 0, 0, a, 30, 0, 0)
					.rot("right_arm", 0, 30, 0, 15, a * 0.4f, -175, 0, 5, a * 0.85f, -175, 0, 5, a, -40, 0, 5)
					.rot("spear", 0, 0, 0, 0, a * 0.4f, 90, 0, 0, a, 90, 0, 0)
					.rot("left_arm", 0, 30, 0, -15, a * 0.4f, -170, 0, -5, a * 0.85f, -170, 0, -5, a, -40, 0, -5)
					.rot("right_leg", 0, -40, 0, 0, a * 0.3f, 20, 0, 0, a * 0.85f, -30, 0, 0, a, -45, 0, 0)
					.rot("left_leg", 0, -40, 0, 0, a * 0.3f, 25, 0, 0, a * 0.85f, 10, 0, 0, a, 20, 0, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.pos("body", 0, 0, 2, 0, r * 0.6f, 0, 2, 0, r, 0, 0, 0)
					.rot("body", 0, 30, 0, 0, r * 0.6f, 30, 0, 0, r, 0, 0, 0)
					.rot("right_arm", 0, -40, 0, 5, r * 0.6f, -40, 0, 5, r, 0, 0, 0)
					.rot("spear", 0, 90, 0, 0, r * 0.6f, 90, 0, 0, r, 0, 0, 0)
					.rot("left_arm", 0, -40, 0, -5, r, 0, 0, 0)
					.rot("right_leg", 0, -45, 0, 0, r, 0, 0, 0)
					.rot("left_leg", 0, 20, 0, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Phase change: thrown back, spear raised to the storm, arms wide. */
	private static AnimationDefinition roar(float length) {
		float l = length;
		return Anim.of(l).smooth()
				.rot("body", 0, 0, 0, 0, l * 0.25f, -18, 0, 0, l * 0.8f, -18, 0, 0, l, 0, 0, 0)
				.rot("head", 0, 0, 0, 0, l * 0.25f, -40, 0, 0, l * 0.8f, -40, 0, 0, l, 0, 0, 0)
				.rot("right_arm", 0, 0, 0, 0, l * 0.25f, -150, 0, 30, l * 0.8f, -150, 0, 30, l, 0, 0, 0)
				.rot("spear", 0, 0, 0, 0, l * 0.25f, 90, 0, 0, l * 0.8f, 90, 0, 0, l, 0, 0, 0)
				.rot("left_arm", 0, 0, 0, 0, l * 0.25f, -30, 0, -60, l * 0.8f, -30, 0, -60, l, 0, 0, 0)
				.build();
	}
}
