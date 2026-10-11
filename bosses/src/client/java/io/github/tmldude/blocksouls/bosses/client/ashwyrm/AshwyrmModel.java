package io.github.tmldude.blocksouls.bosses.client.ashwyrm;

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
import io.github.tmldude.blocksouls.bosses.ashwyrm.Ashwyrm;
import io.github.tmldude.blocksouls.bosses.ashwyrm.AshwyrmConfig;
import io.github.tmldude.blocksouls.bosses.client.render.Anim;
import io.github.tmldude.blocksouls.bosses.client.render.BossModel;
import io.github.tmldude.blocksouls.bosses.client.render.BossRenderState;

/**
 * A four-legged dragon: long two-segment neck rising from the front of the body, a horned head with a hinged jaw,
 * two-segment wings (bone + membrane) and a three-segment tail with a fin. Drawn at 2× so it is ~12 blocks from snout
 * to tail and as wide across the wings. Everything hangs off {@code body}, so the tail-sweep spin turns the whole
 * dragon. Box UVs are mirrored in {@code tools/assets/ashwyrm.py}.
 */
public class AshwyrmModel extends BossModel {
	private final ModelPart body;
	private final ModelPart neck;
	private final ModelPart neck2;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart leftWing;
	private final ModelPart leftWingTip;
	private final ModelPart rightWing;
	private final ModelPart rightWingTip;
	private final ModelPart tail;
	private final ModelPart tail2;
	private final ModelPart tail3;
	private final ModelPart frontLeftLeg;
	private final ModelPart frontRightLeg;
	private final ModelPart rearLeftLeg;
	private final ModelPart rearRightLeg;

	public AshwyrmModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		neck = body.getChild("neck");
		neck2 = neck.getChild("neck2");
		head = neck2.getChild("head");
		jaw = head.getChild("jaw");
		leftWing = body.getChild("left_wing");
		leftWingTip = leftWing.getChild("left_wing_tip");
		rightWing = body.getChild("right_wing");
		rightWingTip = rightWing.getChild("right_wing_tip");
		tail = body.getChild("tail");
		tail2 = tail.getChild("tail2");
		tail3 = tail2.getChild("tail3");
		frontLeftLeg = body.getChild("front_left_leg");
		frontRightLeg = body.getChild("front_right_leg");
		rearLeftLeg = body.getChild("rear_left_leg");
		rearRightLeg = body.getChild("rear_right_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-10, -6, -18, 20, 12, 36)
				.texOffs(120, 70).addBox(-0.5f, -10, -12, 1, 4, 4)
				.texOffs(120, 70).addBox(-0.5f, -10, -2, 1, 4, 4)
				.texOffs(120, 70).addBox(-0.5f, -10, 8, 1, 4, 4),
				PartPose.offset(0, 8, 0));

		PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create()
				.texOffs(0, 48).addBox(-4, -4, -10, 8, 8, 10)
				.texOffs(120, 70).addBox(-0.5f, -8, -8, 1, 4, 4),
				PartPose.offsetAndRotation(0, -1, -18, -0.6f, 0, 0));
		PartDefinition neck2 = neck.addOrReplaceChild("neck2", CubeListBuilder.create()
				.texOffs(36, 48).addBox(-3.5f, -3.5f, -10, 7, 7, 10),
				PartPose.offsetAndRotation(0, 0, -10, 0.35f, 0, 0));
		PartDefinition head = neck2.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(196, 0).addBox(-5, -4, -10, 10, 7, 10)
				.texOffs(196, 17).addBox(-4, -2, -17, 8, 4, 7)
				.texOffs(86, 70).addBox(-4.5f, -6, -3, 2, 2, 6)
				.texOffs(86, 70).mirror().addBox(2.5f, -6, -3, 2, 2, 6),
				PartPose.offsetAndRotation(0, 0, -10, 0.25f, 0, 0));
		head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(196, 28).addBox(-4, 0, -9, 8, 2, 9),
				PartPose.offset(0, 2, -8));

		PartDefinition leftWing = body.addOrReplaceChild("left_wing", CubeListBuilder.create()
				.texOffs(0, 70).addBox(0, -1.5f, -1.5f, 20, 3, 3)
				.texOffs(112, 0).addBox(0, 0, 0, 20, 1, 22),
				PartPose.offset(10, -5, -8));
		leftWing.addOrReplaceChild("left_wing_tip", CubeListBuilder.create()
				.texOffs(46, 70).addBox(0, -1, -1, 18, 2, 2)
				.texOffs(112, 23).addBox(0, 0, 0, 18, 1, 20),
				PartPose.offset(20, 0, 0));
		PartDefinition rightWing = body.addOrReplaceChild("right_wing", CubeListBuilder.create()
				.texOffs(0, 70).mirror().addBox(-20, -1.5f, -1.5f, 20, 3, 3)
				.texOffs(112, 0).mirror().addBox(-20, 0, 0, 20, 1, 22),
				PartPose.offset(-10, -5, -8));
		rightWing.addOrReplaceChild("right_wing_tip", CubeListBuilder.create()
				.texOffs(46, 70).mirror().addBox(-18, -1, -1, 18, 2, 2)
				.texOffs(112, 23).mirror().addBox(-18, 0, 0, 18, 1, 20),
				PartPose.offset(-20, 0, 0));

		PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
				.texOffs(70, 48).addBox(-4, -4, 0, 8, 8, 12),
				PartPose.offsetAndRotation(0, -1, 18, -0.15f, 0, 0));
		PartDefinition tail2 = tail.addOrReplaceChild("tail2", CubeListBuilder.create()
				.texOffs(110, 48).addBox(-3, -3, 0, 6, 6, 10),
				PartPose.offsetAndRotation(0, 0, 12, -0.1f, 0, 0));
		tail2.addOrReplaceChild("tail3", CubeListBuilder.create()
				.texOffs(142, 48).addBox(-2, -2, 0, 4, 4, 10)
				.texOffs(102, 70).addBox(-0.5f, -6, 2, 1, 4, 8),
				PartPose.offsetAndRotation(0, 0, 10, 0.1f, 0, 0));

		body.addOrReplaceChild("front_left_leg", CubeListBuilder.create().texOffs(170, 48).addBox(-3, 0, -3, 6, 12, 6),
				PartPose.offset(8, 4, -12));
		body.addOrReplaceChild("front_right_leg", CubeListBuilder.create().texOffs(170, 48).mirror().addBox(-3, 0, -3, 6, 12, 6),
				PartPose.offset(-8, 4, -12));
		body.addOrReplaceChild("rear_left_leg", CubeListBuilder.create().texOffs(194, 48).addBox(-3.5f, 0, -4, 7, 12, 8),
				PartPose.offset(8, 4, 12));
		body.addOrReplaceChild("rear_right_leg", CubeListBuilder.create().texOffs(194, 48).mirror().addBox(-3.5f, 0, -4, 7, 12, 8),
				PartPose.offset(-8, 4, 12));
		return LayerDefinition.create(mesh, 256, 128);
	}

	@Override
	protected void idle(BossRenderState state) {
		boolean flying = state instanceof AshwyrmRenderer.State wyrm && wyrm.flying;
		float age = state.ageInTicks;
		if (flying) {
			// Wing beats, the tips trailing behind; legs tucked back, neck stretched forward.
			float beat = Mth.sin(age * 0.3f);
			float trail = Mth.sin(age * 0.3f - 0.7f);
			leftWing.zRot += -beat * 0.7f;
			rightWing.zRot += beat * 0.7f;
			leftWingTip.zRot += -trail * 0.5f;
			rightWingTip.zRot += trail * 0.5f;
			body.y += beat * 1.2f;
			frontLeftLeg.xRot += 1.0f;
			frontRightLeg.xRot += 1.0f;
			rearLeftLeg.xRot += 1.2f;
			rearRightLeg.xRot += 1.2f;
			neck.xRot += 0.35f;
			tail.xRot += 0.12f;
		} else {
			// Wings folded back along the body, tips drooping; a heavy four-legged walk.
			leftWing.zRot += -0.35f;
			leftWing.yRot += -1.0f;
			leftWingTip.zRot += 1.0f;
			leftWingTip.yRot += -0.6f;
			rightWing.zRot += 0.35f;
			rightWing.yRot += 1.0f;
			rightWingTip.zRot += -1.0f;
			rightWingTip.yRot += 0.6f;
			float pos = state.walkAnimationPos;
			float speed = Math.min(1f, state.walkAnimationSpeed);
			float swing = Mth.cos(pos * 0.4f) * 0.6f * speed;
			frontLeftLeg.xRot += swing;
			rearRightLeg.xRot += swing;
			frontRightLeg.xRot -= swing;
			rearLeftLeg.xRot -= swing;
			body.zRot += Mth.cos(pos * 0.4f) * 0.03f * speed;
			// Slow breathing.
			body.y += Mth.sin(age * 0.06f) * 0.3f;
		}
		// Neck and tail sway, always.
		neck.yRot += Mth.sin(age * 0.04f) * 0.12f + Mth.clamp(state.yRot, -45, 45) * Mth.DEG_TO_RAD * 0.3f;
		neck2.yRot += Mth.clamp(state.yRot, -45, 45) * Mth.DEG_TO_RAD * 0.2f;
		head.xRot += Mth.sin(age * 0.05f) * 0.05f + Mth.clamp(state.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.3f;
		tail.yRot += Mth.sin(age * 0.07f) * 0.15f;
		tail2.yRot += Mth.sin(age * 0.07f - 0.8f) * 0.2f;
		tail3.yRot += Mth.sin(age * 0.07f - 1.6f) * 0.25f;
		jaw.xRot += Math.max(0, Mth.sin(age * 0.03f)) * 0.08f;
	}

	private static float seconds(int ticks) {
		return Math.max(0.05f, ticks / 20f);
	}

	private static AshwyrmConfig config() {
		return Ashwyrm.config();
	}

	@Override
	protected @Nullable AnimationDefinition animation(@Nullable String attack, Stage stage) {
		if (attack == null) {
			return stage == Stage.STAGGER ? roar(seconds(BossEntity.framework().staggerTicks)) : null;
		}
		return switch (attack) {
			case "bite" -> bite(config().bite, stage);
			case "tail_sweep" -> tailSweep(config().tailSweep, stage);
			case "breath" -> breath(config().breath, stage);
			case "wing_buffet" -> wingBuffet(config().wingBuffet, stage);
			case "fireball_run" -> fireballRun(config().fireballRun, stage);
			case "land" -> land(config().land, stage);
			case "firestorm" -> firestorm(config().firestorm, stage);
			default -> null;
		};
	}

	/** Neck draws back and up with the jaws open, then snaps forward and down. */
	private static @Nullable AnimationDefinition bite(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("neck", 0, 0, 0, 0, w, -20, 0, 0)
					.rot("neck2", 0, 0, 0, 0, w, 10, 0, 0)
					.rot("head", 0, 0, 0, 0, w, -15, 0, 0)
					.rot("jaw", 0, 0, 0, 0, w, 40, 0, 0)
					.pos("body", 0, 0, 0, 0, w, 0, 0, 3)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("neck", 0, -20, 0, 0, a * 0.5f, 30, 0, 0, a, 30, 0, 0)
					.rot("neck2", 0, 10, 0, 0, a * 0.5f, -15, 0, 0, a, -15, 0, 0)
					.rot("head", 0, -15, 0, 0, a * 0.5f, 10, 0, 0, a, 10, 0, 0)
					.rot("jaw", 0, 40, 0, 0, a * 0.6f, 0, 0, 0, a, 0, 0, 0)
					.pos("body", 0, 0, 0, 3, a * 0.5f, 0, 0, -3, a, 0, 0, -3)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("neck", 0, 30, 0, 0, r, 0, 0, 0)
					.rot("neck2", 0, -15, 0, 0, r, 0, 0, 0)
					.rot("head", 0, 10, 0, 0, r, 0, 0, 0)
					.pos("body", 0, 0, 0, -3, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Coils (body twists one way, tail curls the other), then spins a full turn. */
	private static @Nullable AnimationDefinition tailSweep(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("body", 0, 0, 0, 0, w, 0, 30, 0)
					.rot("tail", 0, 0, 0, 0, w, 0, -35, 0)
					.rot("tail2", 0, 0, 0, 0, w, 0, -25, 0)
					.rot("tail3", 0, 0, 0, 0, w, 0, -25, 0)
					.rot("neck", 0, 0, 0, 0, w, 0, -20, 0)
					.pos("body", 0, 0, 0, 0, w, 0, 1.5f, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("body", 0, 0, 30, 0, a, 0, -330, 0)
					.rot("tail", 0, 0, -35, 0, a * 0.3f, 0, 10, 0, a, 0, 10, 0)
					.rot("tail2", 0, 0, -25, 0, a * 0.3f, 0, 10, 0, a, 0, 10, 0)
					.rot("tail3", 0, 0, -25, 0, a * 0.3f, 0, 10, 0, a, 0, 10, 0)
					.rot("neck", 0, 0, -20, 0, a, 0, 0, 0)
					.pos("body", 0, 0, 1.5f, 0, a, 0, 1.5f, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("body", 0, 0, -330, 0, r * 0.5f, 0, -360, 0, r, 0, -360, 0)
					.rot("tail", 0, 0, 10, 0, r, 0, 0, 0)
					.rot("tail2", 0, 0, 10, 0, r, 0, 0, 0)
					.rot("tail3", 0, 0, 10, 0, r, 0, 0, 0)
					.pos("body", 0, 0, 1.5f, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Rears up with the jaws opening, then lowers the head and pours fire forward. */
	private static @Nullable AnimationDefinition breath(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("neck", 0, 0, 0, 0, w, -30, 0, 0)
					.rot("head", 0, 0, 0, 0, w, -25, 0, 0)
					.rot("jaw", 0, 0, 0, 0, w * 0.6f, 10, 0, 0, w, 30, 0, 0)
					.rot("body", 0, 0, 0, 0, w, -8, 0, 0)
					.rot("front_left_leg", 0, 0, 0, 0, w, 8, 0, 0)
					.rot("front_right_leg", 0, 0, 0, 0, w, 8, 0, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("neck", 0, -30, 0, 0, a * 0.1f, 15, 0, 0, a, 15, 0, 0)
					.rot("head", 0, -25, 0, 0, a * 0.1f, 15, 0, 0, a, 15, 0, 0)
					.rot("jaw", 0, 30, 0, 0, a, 30, 0, 0)
					.rot("body", 0, -8, 0, 0, a * 0.1f, 4, 0, 0, a, 4, 0, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("neck", 0, 15, 0, 0, r, 0, 0, 0)
					.rot("head", 0, 15, 0, 0, r, 0, 0, 0)
					.rot("jaw", 0, 30, 0, 0, r * 0.5f, 0, 0, 0)
					.rot("body", 0, 4, 0, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Wings unfold and rise high while it rears, then slam down. */
	private static @Nullable AnimationDefinition wingBuffet(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("left_wing", 0, 0, 0, 0, w, 0, 57, -65)
					.rot("right_wing", 0, 0, 0, 0, w, 0, -57, 65)
					.rot("left_wing_tip", 0, 0, 0, 0, w, 0, 34, -50)
					.rot("right_wing_tip", 0, 0, 0, 0, w, 0, -34, 50)
					.rot("body", 0, 0, 0, 0, w, -12, 0, 0)
					.rot("front_left_leg", 0, 0, 0, 0, w, -25, 0, 0)
					.rot("front_right_leg", 0, 0, 0, 0, w, -25, 0, 0)
					.pos("body", 0, 0, 0, 0, w, 0, -2, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("left_wing", 0, 0, 57, -65, a, 0, 57, 30)
					.rot("right_wing", 0, 0, -57, 65, a, 0, -57, -30)
					.rot("left_wing_tip", 0, 0, 34, -50, a, 0, 34, 20)
					.rot("right_wing_tip", 0, 0, -34, 50, a, 0, -34, -20)
					.rot("body", 0, -12, 0, 0, a, 6, 0, 0)
					.rot("front_left_leg", 0, -25, 0, 0, a, 0, 0, 0)
					.rot("front_right_leg", 0, -25, 0, 0, a, 0, 0, 0)
					.pos("body", 0, 0, -2, 0, a, 0, 1, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("left_wing", 0, 0, 57, 30, r, 0, 0, 0)
					.rot("right_wing", 0, 0, -57, -30, r, 0, 0, 0)
					.rot("left_wing_tip", 0, 0, 34, 20, r, 0, 0, 0)
					.rot("right_wing_tip", 0, 0, -34, -20, r, 0, 0, 0)
					.rot("body", 0, 6, 0, 0, r, 0, 0, 0)
					.pos("body", 0, 0, 1, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** In flight: head down toward the line, jaws snapping open for each fireball. */
	private static @Nullable AnimationDefinition fireballRun(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("neck", 0, 0, 0, 0, w, 25, 0, 0)
					.rot("head", 0, 0, 0, 0, w, 25, 0, 0)
					.rot("jaw", 0, 0, 0, 0, w, 15, 0, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("neck", 0, 25, 0, 0, a, 25, 0, 0)
					.rot("head", 0, 25, 0, 0, a, 25, 0, 0)
					.rot("jaw", 0, 15, 0, 0, a * 0.1f, 45, 0, 0, a * 0.25f, 10, 0, 0, a * 0.4f, 45, 0, 0, a * 0.55f, 10, 0, 0,
							a * 0.7f, 45, 0, 0, a * 0.85f, 10, 0, 0, a, 10, 0, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("neck", 0, 25, 0, 0, r, 0, 0, 0)
					.rot("head", 0, 25, 0, 0, r, 0, 0, 0)
					.rot("jaw", 0, 10, 0, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Flares its wings and reaches down with the legs, slams in, then lies stunned with the head on the ground. */
	private static @Nullable AnimationDefinition land(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("body", 0, 0, 0, 0, w, -15, 0, 0)
					.rot("front_left_leg", 0, 0, 0, 0, w, -40, 0, 0)
					.rot("front_right_leg", 0, 0, 0, 0, w, -40, 0, 0)
					.rot("rear_left_leg", 0, 0, 0, 0, w, -30, 0, 0)
					.rot("rear_right_leg", 0, 0, 0, 0, w, -30, 0, 0)
					.rot("neck", 0, 0, 0, 0, w, -15, 0, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("body", 0, -15, 0, 0, a, 5, 0, 0)
					.rot("front_left_leg", 0, -40, 0, 0, a, 0, 0, 0)
					.rot("front_right_leg", 0, -40, 0, 0, a, 0, 0, 0)
					.rot("rear_left_leg", 0, -30, 0, 0, a, 0, 0, 0)
					.rot("rear_right_leg", 0, -30, 0, 0, a, 0, 0, 0)
					.rot("neck", 0, -15, 0, 0, a, 20, 0, 0)
					.pos("body", 0, 0, 0, 0, a, 0, 3, 0)
					.build();
			case RECOVERY -> {
				float down = Math.min(0.4f, r * 0.2f);
				float up = Math.max(down + 0.05f, r - 0.6f);
				yield Anim.of(r)
						.rot("body", 0, 5, 0, 0, down, 6, 0, 0, up, 6, 0, 0, r, 0, 0, 0)
						.pos("body", 0, 0, 3, 0, down, 0, 4, 0, up, 0, 4, 0, r, 0, 0, 0)
						.rot("neck", 0, 20, 0, 0, down, 40, 0, 0, up, 40, 0, 0, r, 0, 0, 0)
						.rot("neck2", 0, 0, 0, 0, down, 15, 0, 0, up, 15, 0, 0, r, 0, 0, 0)
						.rot("head", 0, 0, 0, 0, down, 15, 0, 0, (down + up) * 0.5f, 10, 15, 0, up, 15, -10, 0, r, 0, 0, 0)
						.rot("jaw", 0, 0, 0, 0, down, 25, 0, 0, up, 25, 0, 0, r, 0, 0, 0)
						.rot("left_wing", 0, 0, 0, 0, down, 0, 40, 45, up, 0, 40, 45, r, 0, 0, 0)
						.rot("right_wing", 0, 0, 0, 0, down, 0, -40, -45, up, 0, -40, -45, r, 0, 0, 0)
						.rot("tail", 0, 0, 0, 0, down, 10, 0, 0, up, 10, 0, 0, r, 0, 0, 0)
						.rot("front_left_leg", 0, 0, 0, 0, down, -20, 0, -15, up, -20, 0, -15, r, 0, 0, 0)
						.rot("front_right_leg", 0, 0, 0, 0, down, -20, 0, 15, up, -20, 0, 15, r, 0, 0, 0)
						.build();
			}
			default -> null;
		};
	}

	/** Rears up on its hind legs, wings spread and jaws open to the sky, then slams back down as the meteors hit. */
	private static @Nullable AnimationDefinition firestorm(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("body", 0, 0, 0, 0, w * 0.5f, -30, 0, 0, w, -32, 0, 0)
					.pos("body", 0, 0, 0, 0, w * 0.5f, 0, -6, 0, w, 0, -6, 0)
					.rot("rear_left_leg", 0, 0, 0, 0, w * 0.5f, 30, 0, 0, w, 32, 0, 0)
					.rot("rear_right_leg", 0, 0, 0, 0, w * 0.5f, 30, 0, 0, w, 32, 0, 0)
					.rot("front_left_leg", 0, 0, 0, 0, w * 0.5f, -20, 0, 0, w, -20, 0, 0)
					.rot("front_right_leg", 0, 0, 0, 0, w * 0.5f, -20, 0, 0, w, -20, 0, 0)
					.rot("neck", 0, 0, 0, 0, w * 0.5f, -30, 0, 0, w, -30, 0, 0)
					.rot("head", 0, 0, 0, 0, w * 0.5f, -30, 0, 0, w, -30, 0, 0)
					.rot("jaw", 0, 0, 0, 0, w * 0.5f, 45, 0, 0, w, 45, 0, 0)
					.rot("left_wing", 0, 0, 0, 0, w * 0.5f, 0, 57, -55, w, 0, 57, -60)
					.rot("right_wing", 0, 0, 0, 0, w * 0.5f, 0, -57, 55, w, 0, -57, 60)
					.rot("left_wing_tip", 0, 0, 0, 0, w, 0, 34, -30)
					.rot("right_wing_tip", 0, 0, 0, 0, w, 0, -34, 30)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("body", 0, -32, 0, 0, a * 0.5f, 6, 0, 0, a, 6, 0, 0)
					.pos("body", 0, 0, -6, 0, a * 0.5f, 0, 1, 0, a, 0, 1, 0)
					.rot("rear_left_leg", 0, 32, 0, 0, a * 0.5f, 0, 0, 0)
					.rot("rear_right_leg", 0, 32, 0, 0, a * 0.5f, 0, 0, 0)
					.rot("front_left_leg", 0, -20, 0, 0, a * 0.5f, 0, 0, 0)
					.rot("front_right_leg", 0, -20, 0, 0, a * 0.5f, 0, 0, 0)
					.rot("neck", 0, -30, 0, 0, a * 0.5f, 15, 0, 0, a, 15, 0, 0)
					.rot("head", 0, -30, 0, 0, a * 0.5f, 5, 0, 0, a, 5, 0, 0)
					.rot("jaw", 0, 45, 0, 0, a, 20, 0, 0)
					.rot("left_wing", 0, 0, 57, -60, a * 0.5f, 0, 57, 20, a, 0, 57, 20)
					.rot("right_wing", 0, 0, -57, 60, a * 0.5f, 0, -57, -20, a, 0, -57, -20)
					.rot("left_wing_tip", 0, 0, 34, -30, a, 0, 34, 10)
					.rot("right_wing_tip", 0, 0, -34, 30, a, 0, -34, -10)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("body", 0, 6, 0, 0, r, 0, 0, 0)
					.pos("body", 0, 0, 1, 0, r, 0, 0, 0)
					.rot("neck", 0, 15, 0, 0, r, 0, 0, 0)
					.rot("head", 0, 5, 0, 0, r, 0, 0, 0)
					.rot("jaw", 0, 20, 0, 0, r, 0, 0, 0)
					.rot("left_wing", 0, 0, 57, 20, r, 0, 0, 0)
					.rot("right_wing", 0, 0, -57, -20, r, 0, 0, 0)
					.rot("left_wing_tip", 0, 0, 34, 10, r, 0, 0, 0)
					.rot("right_wing_tip", 0, 0, -34, -10, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Phase change: rears back, wings half spread, and roars at the sky. */
	private static AnimationDefinition roar(float length) {
		float l = length;
		return Anim.of(l).smooth()
				.rot("body", 0, 0, 0, 0, l * 0.25f, -14, 0, 0, l * 0.8f, -14, 0, 0, l, 0, 0, 0)
				.rot("neck", 0, 0, 0, 0, l * 0.25f, -30, 0, 0, l * 0.8f, -30, 0, 0, l, 0, 0, 0)
				.rot("head", 0, 0, 0, 0, l * 0.25f, -35, 0, 0, l * 0.8f, -35, 0, 0, l, 0, 0, 0)
				.rot("jaw", 0, 0, 0, 0, l * 0.2f, 50, 0, 0, l * 0.85f, 50, 0, 0, l, 0, 0, 0)
				.rot("left_wing", 0, 0, 0, 0, l * 0.25f, 0, 45, -35, l * 0.8f, 0, 45, -35, l, 0, 0, 0)
				.rot("right_wing", 0, 0, 0, 0, l * 0.25f, 0, -45, 35, l * 0.8f, 0, -45, 35, l, 0, 0, 0)
				.rot("front_left_leg", 0, 0, 0, 0, l * 0.25f, -15, 0, 0, l * 0.8f, -15, 0, 0, l, 0, 0, 0)
				.rot("front_right_leg", 0, 0, 0, 0, l * 0.25f, -15, 0, 0, l * 0.8f, -15, 0, 0, l, 0, 0, 0)
				.build();
	}
}
