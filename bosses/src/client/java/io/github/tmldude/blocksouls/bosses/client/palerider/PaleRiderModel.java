package io.github.tmldude.blocksouls.bosses.client.palerider;

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
import io.github.tmldude.blocksouls.bosses.palerider.PaleRider;
import io.github.tmldude.blocksouls.bosses.palerider.PaleRiderConfig;

/**
 * A hooded horseman fused to a spectral horse: one mesh, two halves. {@code mount} holds the {@code horse} and the
 * {@code rider} so a rear lifts both. In phase 2 the horse is hidden and the rider stands on its own, a robe
 * ({@code skirt}) in place of the horse, floating a hand above the ground. Drawn at 1.25× (~3.5 blocks mounted).
 * Box UVs are mirrored in {@code tools/assets/pale_rider_skins.py}; the Wraith Steed reuses the horse half.
 */
public class PaleRiderModel extends BossModel {
	/** Rider pivot height in phase 2: the robe's hem just above the ground. */
	private static final float ON_FOOT_Y = 13;

	private final ModelPart mount;
	private final ModelPart horse;
	private final ModelPart neck;
	private final ModelPart tail;
	private final ModelPart frontLeftLeg;
	private final ModelPart frontRightLeg;
	private final ModelPart backLeftLeg;
	private final ModelPart backRightLeg;
	private final ModelPart rider;
	private final ModelPart riderHead;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart lance;
	private final ModelPart cloak;
	private final ModelPart skirt;

	public PaleRiderModel(ModelPart root) {
		super(root);
		mount = root.getChild("mount");
		horse = mount.getChild("horse");
		neck = horse.getChild("neck");
		tail = horse.getChild("tail");
		frontLeftLeg = horse.getChild("front_left_leg");
		frontRightLeg = horse.getChild("front_right_leg");
		backLeftLeg = horse.getChild("back_left_leg");
		backRightLeg = horse.getChild("back_right_leg");
		rider = mount.getChild("rider");
		riderHead = rider.getChild("rider_head");
		rightArm = rider.getChild("right_arm");
		leftArm = rider.getChild("left_arm");
		lance = rightArm.getChild("lance");
		cloak = rider.getChild("cloak");
		skirt = rider.getChild("skirt");
	}

	/**
	 * The spectral horse (shared with the Wraith Steed): back at y 0, hooves at y 24, head toward -z. Texture
	 * regions: body (0,0), neck (68,0), head (90,0), legs (68,20), tail (80,20).
	 */
	public static PartDefinition addHorse(PartDefinition parent) {
		PartDefinition horse = parent.addOrReplaceChild("horse", CubeListBuilder.create(), PartPose.ZERO);
		horse.addOrReplaceChild("horse_body", CubeListBuilder.create().texOffs(0, 0).addBox(-5, -5, -12, 10, 10, 24), PartPose.offset(0, 5, 0));
		PartDefinition neck = horse.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(68, 0).addBox(-2.5f, -13, -3, 5, 13, 6),
				PartPose.offsetAndRotation(0, 3, -10, 0.52f, 0, 0));
		neck.addOrReplaceChild("horse_head", CubeListBuilder.create().texOffs(90, 0).addBox(-3, -4, -8, 6, 7, 10), PartPose.offset(0, -12, 0));
		horse.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(80, 20).addBox(-1.5f, 0, -1.5f, 3, 14, 3),
				PartPose.offsetAndRotation(0, 1, 12, 0.6f, 0, 0));
		CubeListBuilder leg = CubeListBuilder.create().texOffs(68, 20).addBox(-1.5f, 0, -1.5f, 3, 14, 3);
		CubeListBuilder mirrored = CubeListBuilder.create().texOffs(68, 20).mirror().addBox(-1.5f, 0, -1.5f, 3, 14, 3);
		horse.addOrReplaceChild("front_left_leg", mirrored, PartPose.offset(3.5f, 10, -9));
		horse.addOrReplaceChild("front_right_leg", leg, PartPose.offset(-3.5f, 10, -9));
		horse.addOrReplaceChild("back_left_leg", mirrored, PartPose.offset(3.5f, 10, 9));
		horse.addOrReplaceChild("back_right_leg", leg, PartPose.offset(-3.5f, 10, 9));
		return horse;
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition mount = mesh.getRoot().addOrReplaceChild("mount", CubeListBuilder.create(), PartPose.ZERO);
		addHorse(mount);
		PartDefinition rider = mount.addOrReplaceChild("rider", CubeListBuilder.create().texOffs(0, 34).addBox(-4, -12, -2.5f, 8, 12, 5),
				PartPose.offset(0, 0, 1));
		rider.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(92, 20).addBox(-4.5f, 0, -3, 9, 10, 6), PartPose.ZERO);
		rider.addOrReplaceChild("rider_head", CubeListBuilder.create().texOffs(26, 34).addBox(-4, -8, -4, 8, 8, 8), PartPose.offset(0, -12, 0));
		rider.addOrReplaceChild("cloak", CubeListBuilder.create().texOffs(70, 40).addBox(-5, 0, 0, 10, 14, 1), PartPose.offset(0, -11, 2.5f));
		PartDefinition rightArm = rider.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(58, 34).addBox(-1.5f, -1, -1.5f, 3, 12, 3),
				PartPose.offset(-5.5f, -11, 0));
		rightArm.addOrReplaceChild("lance", CubeListBuilder.create()
				.texOffs(0, 60).addBox(-1, -1, -30, 2, 2, 40)
				.texOffs(90, 60).addBox(-1.5f, -1.5f, -34, 3, 3, 4),
				PartPose.offset(0, 10, 0));
		rider.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(58, 34).mirror().addBox(-1.5f, -1, -1.5f, 3, 12, 3),
				PartPose.offset(5.5f, -11, 0));
		return LayerDefinition.create(mesh, 128, 128);
	}

	/** Walk cycle for the horse half (also used by the Wraith Steed). */
	public static void gallop(ModelPart neck, ModelPart tail, ModelPart fl, ModelPart fr, ModelPart bl, ModelPart br, float pos, float speed, float age) {
		float swing = 0.9f * speed;
		fl.xRot = Mth.cos(pos * 0.6f) * swing;
		br.xRot = Mth.cos(pos * 0.6f) * swing;
		fr.xRot = Mth.cos(pos * 0.6f + Mth.PI) * swing;
		bl.xRot = Mth.cos(pos * 0.6f + Mth.PI) * swing;
		neck.xRot = 0.52f + Mth.cos(pos * 0.6f) * 0.08f * speed + Mth.sin(age * 0.05f) * 0.03f;
		tail.xRot = 0.6f + speed * 0.5f + Mth.sin(age * 0.15f) * 0.08f;
		tail.zRot = Mth.sin(age * 0.11f) * 0.15f;
	}

	@Override
	protected void idle(BossRenderState state) {
		boolean onFoot = state.phase >= 1;
		horse.visible = !onFoot;
		skirt.visible = onFoot;
		float pos = state.walkAnimationPos;
		float speed = Math.min(1f, state.walkAnimationSpeed);
		float age = state.ageInTicks;
		// The cloak streams back with speed and sways.
		cloak.xRot = 0.15f + speed * 0.6f + Mth.sin(age * 0.09f) * 0.08f;
		cloak.zRot = Mth.sin(age * 0.07f) * 0.05f;
		riderHead.yRot = Mth.clamp(state.yRot, -45, 45) * Mth.DEG_TO_RAD * 0.6f;
		riderHead.xRot = Mth.clamp(state.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.5f;
		leftArm.xRot = -0.35f;
		leftArm.zRot = -0.12f;
		if (!onFoot) {
			gallop(neck, tail, frontLeftLeg, frontRightLeg, backLeftLeg, backRightLeg, pos, speed, age);
			// Lance couched: the arm forward, the lance level and pointing ahead.
			rightArm.xRot = -0.9f;
			lance.xRot = 0.9f;
			rider.xRot = 0.08f * speed;
			rider.y += Mth.cos(pos * 1.2f) * 0.6f * speed;
		} else {
			// On foot it hovers: a slow bob, the robe a hand above the ground.
			rider.y = ON_FOOT_Y - 1 - Mth.sin(age * 0.08f) * 0.8f;
			rider.xRot = 0.05f;
			skirt.xRot = Mth.sin(age * 0.06f) * 0.05f + speed * 0.3f;
			rightArm.xRot = -0.4f;
			lance.xRot = 0.4f;
		}
	}

	private static float seconds(int ticks) {
		return Math.max(0.05f, ticks / 20f);
	}

	private static PaleRiderConfig config() {
		return PaleRider.config();
	}

	@Override
	protected @Nullable AnimationDefinition animation(@Nullable String attack, Stage stage) {
		if (attack == null) {
			return stage == Stage.STAGGER ? scream(seconds(BossEntity.framework().staggerTicks)) : null;
		}
		return switch (attack) {
			case "lance" -> lance(config().lance, stage);
			case "sweep" -> sweep(config().sweep, stage);
			case "stomp" -> stomp(config().stomp, stage);
			case "call" -> call(config().wraithCall, stage);
			case "spears" -> spears(config().spears, stage);
			case "blink" -> blink(config().blink, stage);
			case "slash" -> slash(config().slash, stage);
			default -> null;
		};
	}

	/** Leans low over the neck, lance dropped level; legs pound through both passes; sits up after. */
	private static @Nullable AnimationDefinition lance(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("rider", 0, 0, 0, 0, w, 22, 0, 0)
					.rot("right_arm", 0, 0, 0, 0, w, 12, 0, 0)
					.rot("lance", 0, 0, 0, 0, w, -12, 0, 0)
					.rot("front_left_leg", 0, 0, 0, 0, w * 0.5f, -45, 0, 0, w, 10, 0, 0)
					.rot("front_right_leg", 0, 0, 0, 0, w * 0.5f, 10, 0, 0, w, -45, 0, 0)
					.build();
			case ACTIVE -> {
				Anim anim = Anim.of(a)
						.rot("rider", 0, 22, 0, 0, a, 22, 0, 0)
						.rot("right_arm", 0, 12, 0, 0, a, 12, 0, 0)
						.rot("lance", 0, -12, 0, 0, a, -12, 0, 0);
				// A gallop cycle every 0.3 s for the whole stage.
				int steps = Math.max(2, (int) (a / 0.15f));
				float[] front = new float[(steps + 1) * 4];
				float[] back = new float[(steps + 1) * 4];
				for (int i = 0; i <= steps; i++) {
					float t = a * i / steps;
					float x = i % 2 == 0 ? -50 : 40;
					front[i * 4] = t;
					front[i * 4 + 1] = x;
					back[i * 4] = t;
					back[i * 4 + 1] = -x;
				}
				anim.rot("front_left_leg", front).rot("front_right_leg", front).rot("back_left_leg", back).rot("back_right_leg", back);
				yield anim.build();
			}
			case RECOVERY -> Anim.of(r)
					.rot("rider", 0, 22, 0, 0, r, 0, 0, 0)
					.rot("right_arm", 0, 12, 0, 0, r, 0, 0, 0)
					.rot("lance", 0, -12, 0, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Twists far back with the lance out wide, then whips it around. */
	private static @Nullable AnimationDefinition sweep(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("rider", 0, 0, 0, 0, w, 0, 70, 0)
					.rot("right_arm", 0, 0, 0, 0, w, 40, 0, 60)
					.rot("lance", 0, 0, 0, 0, w, -30, 0, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("rider", 0, 0, 70, 0, a, 0, -100, 0)
					.rot("right_arm", 0, 40, 0, 60, a, 40, 0, 60)
					.rot("lance", 0, -30, 0, 0, a, -30, 0, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("rider", 0, 0, -100, 0, r * 0.4f, 0, -100, 0, r, 0, 0, 0)
					.rot("right_arm", 0, 40, 0, 60, r, 0, 0, 0)
					.rot("lance", 0, -30, 0, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** The horse rears high, forelegs kicking, then crashes down. */
	private static @Nullable AnimationDefinition stomp(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("mount", 0, 0, 0, 0, w * 0.7f, -35, 0, 0, w, -38, 0, 0)
					.pos("mount", 0, 0, 0, 0, w * 0.7f, 0, 7, 0, w, 0, 8, 0)
					.rot("front_left_leg", 0, 0, 0, 0, w * 0.4f, -70, 0, 0, w * 0.7f, -20, 0, 0, w, -80, 0, 0)
					.rot("front_right_leg", 0, 0, 0, 0, w * 0.4f, -20, 0, 0, w * 0.7f, -80, 0, 0, w, -30, 0, 0)
					.rot("back_left_leg", 0, 0, 0, 0, w, 35, 0, 0)
					.rot("back_right_leg", 0, 0, 0, 0, w, 35, 0, 0)
					.rot("rider", 0, 0, 0, 0, w, 20, 0, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("mount", 0, -38, 0, 0, a * 0.4f, 6, 0, 0, a, 4, 0, 0)
					.pos("mount", 0, 0, 8, 0, a * 0.4f, 0, -1, 0, a, 0, 0, 0)
					.rot("front_left_leg", 0, -80, 0, 0, a * 0.4f, 0, 0, 0)
					.rot("front_right_leg", 0, -30, 0, 0, a * 0.4f, 0, 0, 0)
					.rot("back_left_leg", 0, 35, 0, 0, a * 0.4f, 0, 0, 0)
					.rot("back_right_leg", 0, 35, 0, 0, a * 0.4f, 0, 0, 0)
					.rot("rider", 0, 20, 0, 0, a * 0.4f, -10, 0, 0, a, -10, 0, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("mount", 0, 4, 0, 0, r, 0, 0, 0)
					.rot("rider", 0, -10, 0, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Raises the lance to the sky and throws its head back. */
	private static @Nullable AnimationDefinition call(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("right_arm", 0, 0, 0, 0, w * 0.5f, -120, 0, 15, w, -125, 0, 15)
					.rot("lance", 0, 0, 0, 0, w * 0.5f, 30, 0, 0)
					.rot("rider_head", 0, 0, 0, 0, w * 0.5f, -35, 0, 0)
					.rot("left_arm", 0, 0, 0, 0, w, -20, 0, -60)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("right_arm", 0, -125, 0, 15, a, -125, 0, 15)
					.rot("lance", 0, 30, 0, 0, a, 30, 0, 0)
					.rot("rider_head", 0, -35, 0, 0, a, -35, 0, 0)
					.rot("left_arm", 0, -20, 0, -60, a, -20, 0, -60)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("right_arm", 0, -125, 0, 15, r, 0, 0, 0)
					.rot("lance", 0, 30, 0, 0, r, 0, 0, 0)
					.rot("rider_head", 0, -35, 0, 0, r, 0, 0, 0)
					.rot("left_arm", 0, -20, 0, -60, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Draws back to throw, then hurls (the spears themselves are particles). */
	private static @Nullable AnimationDefinition spears(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("right_arm", 0, 0, 0, 0, w, -150, 0, 10)
					.rot("lance", 0, 0, 0, 0, w, 60, 0, 0)
					.rot("rider", 0, 0, 0, 0, w, -10, 25, 0)
					.rot("left_arm", 0, 0, 0, 0, w, -70, 0, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("right_arm", 0, -150, 0, 10, a * 0.4f, -50, 0, 0, a, -45, 0, 0)
					.rot("lance", 0, 60, 0, 0, a * 0.4f, 10, 0, 0, a, 10, 0, 0)
					.rot("rider", 0, -10, 25, 0, a * 0.4f, 15, -15, 0, a, 15, -15, 0)
					.rot("left_arm", 0, -70, 0, 0, a, -10, 0, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("right_arm", 0, -45, 0, 0, r, 0, 0, 0)
					.rot("lance", 0, 10, 0, 0, r, 0, 0, 0)
					.rot("rider", 0, 15, -15, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Folds into its cloak (the shimmer), reappears mid-swing. */
	private static @Nullable AnimationDefinition blink(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("rider", 0, 0, 0, 0, w, 25, 0, 0)
					.pos("rider", 0, 0, 0, 0, w, 0, -2, 0)
					.rot("right_arm", 0, 0, 0, 0, w, -20, 0, 50)
					.rot("left_arm", 0, 0, 0, 0, w, -20, 0, -50)
					.rot("rider_head", 0, 0, 0, 0, w, 25, 0, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("rider", 0, 0, 60, 0, a, 0, -60, 0)
					.rot("right_arm", 0, -90, 0, 70, a, -90, 0, -20)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("rider", 0, 0, -60, 0, r, 0, 0, 0)
					.rot("right_arm", 0, -90, 0, -20, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** A short overhead cut. */
	private static @Nullable AnimationDefinition slash(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("right_arm", 0, 0, 0, 0, w, -160, 0, 0)
					.rot("rider", 0, 0, 0, 0, w, -8, 0, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("right_arm", 0, -160, 0, 0, a, -20, 0, 0)
					.rot("rider", 0, -8, 0, 0, a, 15, 0, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("right_arm", 0, -20, 0, 0, r, 0, 0, 0)
					.rot("rider", 0, 15, 0, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Phase change: the horse is gone; the rider throws its arms wide and screams. */
	private static AnimationDefinition scream(float l) {
		return Anim.of(l).smooth()
				.rot("rider", 0, 0, 0, 0, l * 0.25f, -20, 0, 0, l * 0.8f, -20, 0, 0, l, 0, 0, 0)
				.rot("rider_head", 0, 0, 0, 0, l * 0.25f, -40, 0, 0, l * 0.8f, -40, 0, 0, l, 0, 0, 0)
				.rot("right_arm", 0, 0, 0, 0, l * 0.25f, -40, 0, 70, l * 0.8f, -40, 0, 70, l, 0, 0, 0)
				.rot("left_arm", 0, 0, 0, 0, l * 0.25f, -40, 0, -70, l * 0.8f, -40, 0, -70, l, 0, 0, 0)
				.build();
	}
}
