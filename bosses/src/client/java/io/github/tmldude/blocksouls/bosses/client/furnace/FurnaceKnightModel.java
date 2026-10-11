package io.github.tmldude.blocksouls.bosses.client.furnace;

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
import io.github.tmldude.blocksouls.bosses.furnace.FurnaceKnight;
import io.github.tmldude.blocksouls.bosses.furnace.FurnaceKnightConfig;

/**
 * A broad armored knight whose helm is a furnace: a square grate (the {@code visor}) on the front that swings open for
 * the breath. Huge pauldrons, a plated skirt, a cleaver as long as a man held in the right hand. Drawn at 0.95× so it
 * stands 3.2 blocks tall. Box UVs are mirrored in {@code tools/assets/boss_skins.py}.
 */
public class FurnaceKnightModel extends BossModel {
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public FurnaceKnightModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		head = body.getChild("head");
		rightArm = body.getChild("right_arm");
		leftArm = body.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-9, -22, -5.5f, 18, 22, 11)
				.texOffs(0, 34).addBox(-9.5f, -2, -6, 19, 7, 12),
				PartPose.offset(0, 4, 0));
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(60, 0).addBox(-6, -12, -6, 12, 12, 12)
				.texOffs(110, 0).addBox(-2, -16, -1, 4, 5, 4),
				PartPose.offset(0, -22, 0));
		head.addOrReplaceChild("visor", CubeListBuilder.create().texOffs(104, 44).addBox(-4.5f, 0, -1, 9, 6, 1),
				PartPose.offset(0, -9, -6));
		PartDefinition rightArm = body.addOrReplaceChild("right_arm", CubeListBuilder.create()
				.texOffs(0, 54).addBox(-5, -2, -4, 7, 22, 8)
				.texOffs(32, 54).addBox(-7, -5, -5.5f, 10, 7, 11),
				PartPose.offset(-11.5f, -19, 0));
		rightArm.addOrReplaceChild("cleaver", CubeListBuilder.create()
				.texOffs(104, 26).addBox(-1, -1, -3, 2, 2, 6)
				.texOffs(104, 36).addBox(-1, -2, -5, 2, 4, 2)
				.texOffs(0, 88).addBox(-0.5f, -1, -33, 1, 10, 28),
				PartPose.offset(-1.5f, 19, 0));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create()
				.texOffs(0, 54).mirror().addBox(-2, -2, -4, 7, 22, 8)
				.texOffs(32, 54).mirror().addBox(-3, -5, -5.5f, 10, 7, 11),
				PartPose.offset(11.5f, -19, 0));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(76, 26).addBox(-3, 0, -3.5f, 6, 20, 7),
				PartPose.offset(-4.5f, 4, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(76, 26).mirror().addBox(-3, 0, -3.5f, 6, 20, 7),
				PartPose.offset(4.5f, 4, 0));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	protected void idle(BossRenderState state) {
		float pos = state.walkAnimationPos;
		float speed = Math.min(1f, state.walkAnimationSpeed);
		rightLeg.xRot = Mth.cos(pos * 0.5f) * 0.8f * speed;
		leftLeg.xRot = Mth.cos(pos * 0.5f + Mth.PI) * 0.8f * speed;
		leftArm.xRot = Mth.cos(pos * 0.5f) * 0.4f * speed;
		// The cleaver hangs forward and low, ready.
		rightArm.xRot = -0.35f + Mth.cos(pos * 0.5f + Mth.PI) * 0.15f * speed;
		rightArm.zRot = 0.08f;
		leftArm.zRot = -0.08f;
		body.zRot = Mth.cos(pos * 0.5f) * 0.05f * speed;
		// Slow heavy breathing.
		body.xRot = 0.05f + Mth.sin(state.ageInTicks * 0.06f) * 0.025f;
		head.yRot = Mth.clamp(state.yRot, -40, 40) * Mth.DEG_TO_RAD * 0.5f;
		head.xRot = Mth.clamp(state.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.4f;
	}

	private static float seconds(int ticks) {
		return Math.max(0.05f, ticks / 20f);
	}

	private static FurnaceKnightConfig config() {
		return FurnaceKnight.config();
	}

	@Override
	protected @Nullable AnimationDefinition animation(@Nullable String attack, Stage stage) {
		if (attack == null) {
			return stage == Stage.STAGGER ? roar(seconds(BossEntity.framework().staggerTicks)) : null;
		}
		return switch (attack) {
			case "slam" -> slam(config().slam, stage);
			case "sweep" -> sweep(config().sweep, stage);
			case "breath" -> breath(config().breath, stage);
			case "charge" -> charge(config().charge, stage);
			case "pillars" -> pillars(config().pillars, stage);
			default -> null;
		};
	}

	/** Both hands raise the cleaver overhead, lean back... then it comes down into the ground. */
	private static @Nullable AnimationDefinition slam(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("right_arm", 0, 0, 0, 0, w * 0.8f, -150, 10, 0, w, -155, 10, 0)
					.rot("left_arm", 0, 0, 0, 0, w * 0.8f, -140, -25, 0, w, -145, -25, 0)
					.rot("body", 0, 0, 0, 0, w, -12, 0, 0)
					.rot("head", 0, 0, 0, 0, w, -10, 0, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("right_arm", 0, -155, 10, 0, a * 0.4f, 35, 10, 0, a, 40, 10, 0)
					.rot("left_arm", 0, -145, -25, 0, a * 0.4f, 25, -25, 0, a, 30, -25, 0)
					.rot("body", 0, -12, 0, 0, a * 0.4f, 25, 0, 0, a, 28, 0, 0)
					.pos("body", 0, 0, 0, 0, a, 0, 3, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("right_arm", 0, 40, 10, 0, r * 0.6f, 40, 10, 0, r, 0, 0, 0)
					.rot("left_arm", 0, 30, -25, 0, r * 0.6f, 30, -25, 0, r, 0, 0, 0)
					.rot("body", 0, 28, 0, 0, r * 0.6f, 25, 0, 0, r, 0, 0, 0)
					.pos("body", 0, 0, 3, 0, r * 0.6f, 0, 3, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Twists far to one side with the blade held low, then whips around. */
	private static @Nullable AnimationDefinition sweep(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("body", 0, 0, 0, 0, w, 10, 50, 0)
					.rot("right_arm", 0, 0, 0, 0, w, -70, 70, 20)
					.rot("left_arm", 0, 0, 0, 0, w, -30, 0, -20)
					.pos("body", 0, 0, 0, 0, w, 0, 2, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("body", 0, 10, 50, 0, a, 10, -70, 0)
					.rot("right_arm", 0, -70, 70, 20, a, -75, -80, 20)
					.rot("left_arm", 0, -30, 0, -20, a, -20, 0, -40)
					.pos("body", 0, 0, 2, 0, a, 0, 2, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("body", 0, 10, -70, 0, r * 0.5f, 10, -70, 0, r, 0, 0, 0)
					.rot("right_arm", 0, -75, -80, 20, r * 0.5f, -75, -80, 20, r, 0, 0, 0)
					.rot("left_arm", 0, -20, 0, -40, r, 0, 0, 0)
					.pos("body", 0, 0, 2, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** The grate swings open and the knight leans in, head tilted down at the ground before it. */
	private static @Nullable AnimationDefinition breath(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("visor", 0, 0, 0, 0, w * 0.7f, -105, 0, 0)
					.rot("head", 0, 0, 0, 0, w * 0.5f, -20, 0, 0, w, 15, 0, 0)
					.rot("body", 0, 0, 0, 0, w * 0.5f, -10, 0, 0, w, 12, 0, 0)
					.rot("left_arm", 0, 0, 0, 0, w, -20, 0, -35)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("visor", 0, -105, 0, 0, a, -105, 0, 0)
					.rot("head", 0, 15, 0, 0, a * 0.5f, 20, 0, 0, a, 15, 0, 0)
					.rot("body", 0, 12, 0, 0, a, 12, 0, 0)
					.rot("left_arm", 0, -20, 0, -35, a, -20, 0, -35)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("visor", 0, -105, 0, 0, r * 0.7f, 0, 0, 0)
					.rot("head", 0, 15, 0, 0, r, 0, 0, 0)
					.rot("body", 0, 12, 0, 0, r, 0, 0, 0)
					.rot("left_arm", 0, -20, 0, -35, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Shoulder down, cleaver forward like a lance, then a heavy run. */
	private static @Nullable AnimationDefinition charge(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("body", 0, 0, 0, 0, w, 28, 0, 0)
					.pos("body", 0, 0, 0, 0, w, 0, 3, 0)
					.rot("right_arm", 0, 0, 0, 0, w, -70, 0, 0)
					.rot("left_arm", 0, 0, 0, 0, w, -40, 0, -10)
					.rot("right_leg", 0, 0, 0, 0, w, -35, 0, 0)
					.rot("left_leg", 0, 0, 0, 0, w, 25, 0, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("body", 0, 28, 0, 0, a, 28, 0, 0)
					.pos("body", 0, 0, 3, 0, a, 0, 3, 0)
					.rot("right_arm", 0, -70, 0, 0, a, -70, 0, 0)
					.rot("left_arm", 0, -40, 0, -10, a, -40, 0, -10)
					.rot("right_leg", 0, -35, 0, 0, a * 0.25f, 35, 0, 0, a * 0.5f, -35, 0, 0, a * 0.75f, 35, 0, 0, a, -35, 0, 0)
					.rot("left_leg", 0, 25, 0, 0, a * 0.25f, -35, 0, 0, a * 0.5f, 25, 0, 0, a * 0.75f, -35, 0, 0, a, 25, 0, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("body", 0, 28, 0, 0, r * 0.3f, 35, 0, 0, r, 0, 0, 0)
					.pos("body", 0, 0, 3, 0, r, 0, 0, 0)
					.rot("right_arm", 0, -70, 0, 0, r, 0, 0, 0)
					.rot("left_arm", 0, -40, 0, -10, r, 0, 0, 0)
					.rot("right_leg", 0, -35, 0, 0, r, 0, 0, 0)
					.rot("left_leg", 0, 25, 0, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Raises both arms to the sky, then drives the cleaver into the ground as the pillars erupt. */
	private static @Nullable AnimationDefinition pillars(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("right_arm", 0, 0, 0, 0, w * 0.6f, -175, 0, 25, w, -175, 0, 25)
					.rot("left_arm", 0, 0, 0, 0, w * 0.6f, -175, 0, -25, w, -175, 0, -25)
					.rot("head", 0, 0, 0, 0, w * 0.6f, -30, 0, 0)
					.rot("body", 0, 0, 0, 0, w * 0.6f, -10, 0, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("right_arm", 0, -175, 0, 25, a * 0.3f, -20, 0, 10, a, -20, 0, 10)
					.rot("left_arm", 0, -175, 0, -25, a * 0.3f, -20, 0, -10, a, -20, 0, -10)
					.rot("head", 0, -30, 0, 0, a * 0.3f, 10, 0, 0, a, 10, 0, 0)
					.rot("body", 0, -10, 0, 0, a * 0.3f, 30, 0, 0, a, 30, 0, 0)
					.pos("body", 0, 0, 0, 0, a * 0.3f, 0, 4, 0, a, 0, 4, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("right_arm", 0, -20, 0, 10, r, 0, 0, 0)
					.rot("left_arm", 0, -20, 0, -10, r, 0, 0, 0)
					.rot("head", 0, 10, 0, 0, r, 0, 0, 0)
					.rot("body", 0, 30, 0, 0, r, 0, 0, 0)
					.pos("body", 0, 0, 4, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Phase change: rears back, arms wide, grate thrown open, and roars. */
	private static AnimationDefinition roar(float length) {
		float l = length;
		return Anim.of(l).smooth()
				.rot("body", 0, 0, 0, 0, l * 0.25f, -18, 0, 0, l * 0.8f, -18, 0, 0, l, 0, 0, 0)
				.rot("head", 0, 0, 0, 0, l * 0.25f, -35, 0, 0, l * 0.8f, -35, 0, 0, l, 0, 0, 0)
				.rot("visor", 0, 0, 0, 0, l * 0.2f, -110, 0, 0, l * 0.85f, -110, 0, 0, l, 0, 0, 0)
				.rot("right_arm", 0, 0, 0, 0, l * 0.25f, -30, 0, 50, l * 0.8f, -30, 0, 50, l, 0, 0, 0)
				.rot("left_arm", 0, 0, 0, 0, l * 0.25f, -30, 0, -50, l * 0.8f, -30, 0, -50, l, 0, 0, 0)
				.build();
	}
}
