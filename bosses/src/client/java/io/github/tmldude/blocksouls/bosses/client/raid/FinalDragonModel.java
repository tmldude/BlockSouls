package io.github.tmldude.blocksouls.bosses.client.raid;

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
import io.github.tmldude.blocksouls.bosses.raid.FinalDragon;
import io.github.tmldude.blocksouls.bosses.raid.FinalRaidConfig;

/**
 * The final dragon: gaunt and void-black, a narrow ribcage of a body with a bone ridge, a long three-segment neck, a
 * horned crown on a narrow skull with a hinged jaw, five-segment tail ending in a blade, thin legs, and wings far
 * larger than vanilla's (each arm 70 px: about 9.5 blocks at the renderer's 2× scale). Violet light leaks from the
 * cracks (glow layers). Box UVs mirror {@code tools/assets/raid_skins.py}; texture 256×128.
 */
public class FinalDragonModel extends BossModel {
	private final ModelPart body;
	private final ModelPart neck1;
	private final ModelPart neck2;
	private final ModelPart neck3;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart leftWing;
	private final ModelPart leftWingTip;
	private final ModelPart rightWing;
	private final ModelPart rightWingTip;
	private final ModelPart[] tail = new ModelPart[5];
	private final ModelPart[] legs = new ModelPart[4];
	private final ModelPart[] shins = new ModelPart[4];

	private static final String[] LEGS = {"front_left_leg", "front_right_leg", "back_left_leg", "back_right_leg"};

	public FinalDragonModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		neck1 = body.getChild("neck1");
		neck2 = neck1.getChild("neck2");
		neck3 = neck2.getChild("neck3");
		head = neck3.getChild("head");
		jaw = head.getChild("jaw");
		leftWing = body.getChild("left_wing");
		leftWingTip = leftWing.getChild("left_wing_tip");
		rightWing = body.getChild("right_wing");
		rightWingTip = rightWing.getChild("right_wing_tip");
		ModelPart segment = body;
		for (int i = 0; i < tail.length; i++) {
			segment = segment.getChild("tail" + (i + 1));
			tail[i] = segment;
		}
		for (int i = 0; i < legs.length; i++) {
			legs[i] = body.getChild(LEGS[i]);
			shins[i] = legs[i].getChild(LEGS[i] + "_shin");
		}
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-7, -5, -15, 14, 10, 30)
				.texOffs(88, 0).addBox(-1, -8, -15, 2, 3, 30),
				PartPose.offset(0, 6, 0));

		// Neck: three segments forward (-z), then the skull.
		CubeListBuilder neckBox = CubeListBuilder.create().texOffs(152, 0).addBox(-3, -3, -8, 6, 6, 8);
		PartDefinition neck1 = body.addOrReplaceChild("neck1", neckBox, PartPose.offsetAndRotation(0, -1, -15, 0.25f, 0, 0));
		PartDefinition neck2 = neck1.addOrReplaceChild("neck2", neckBox, PartPose.offsetAndRotation(0, 0, -8, 0.1f, 0, 0));
		PartDefinition neck3 = neck2.addOrReplaceChild("neck3", neckBox, PartPose.offsetAndRotation(0, 0, -8, -0.15f, 0, 0));
		PartDefinition head = neck3.addOrReplaceChild("head", CubeListBuilder.create().texOffs(180, 0).addBox(-5, -4, -14, 10, 8, 14),
				PartPose.offsetAndRotation(0, 0, -8, -0.2f, 0, 0));
		head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(180, 22).addBox(-4, 0, -12, 8, 3, 12), PartPose.offset(0, 3, -2));
		// The crown: two great horns swept back, three short ones between them.
		CubeListBuilder bigHorn = CubeListBuilder.create().texOffs(236, 0).addBox(-1, -10, -1, 2, 10, 2);
		CubeListBuilder horn = CubeListBuilder.create().texOffs(228, 0).addBox(-1, -7, -1, 2, 7, 2);
		head.addOrReplaceChild("horn_left", bigHorn, PartPose.offsetAndRotation(4, -3, -2, -0.9f, 0, 0.35f));
		head.addOrReplaceChild("horn_right", bigHorn, PartPose.offsetAndRotation(-4, -3, -2, -0.9f, 0, -0.35f));
		head.addOrReplaceChild("horn_mid", horn, PartPose.offsetAndRotation(0, -4, -5, -0.6f, 0, 0));
		head.addOrReplaceChild("horn_mid_left", horn, PartPose.offsetAndRotation(2.5f, -4, -3, -0.7f, 0, 0.2f));
		head.addOrReplaceChild("horn_mid_right", horn, PartPose.offsetAndRotation(-2.5f, -4, -3, -0.7f, 0, -0.2f));

		// Wings: a bone arm with a membrane hanging back, a second joint, another membrane. Zero-thickness membranes.
		PartDefinition leftWing = body.addOrReplaceChild("left_wing", CubeListBuilder.create()
				.texOffs(0, 40).addBox(0, -1.5f, -2, 36, 3, 4)
				.texOffs(0, 52).addBox(0, 0, 2, 36, 0, 26),
				PartPose.offset(7, -4, -8));
		leftWing.addOrReplaceChild("left_wing_tip", CubeListBuilder.create()
				.texOffs(0, 47).addBox(0, -1, -1, 34, 2, 2)
				.texOffs(0, 78).addBox(0, 0, 1, 34, 0, 26),
				PartPose.offset(36, 0, 0));
		PartDefinition rightWing = body.addOrReplaceChild("right_wing", CubeListBuilder.create()
				.texOffs(0, 40).mirror().addBox(-36, -1.5f, -2, 36, 3, 4)
				.texOffs(0, 52).mirror().addBox(-36, 0, 2, 36, 0, 26),
				PartPose.offset(-7, -4, -8));
		rightWing.addOrReplaceChild("right_wing_tip", CubeListBuilder.create()
				.texOffs(0, 47).mirror().addBox(-34, -1, -1, 34, 2, 2)
				.texOffs(0, 78).mirror().addBox(-34, 0, 1, 34, 0, 26),
				PartPose.offset(-36, 0, 0));

		// Tail: five segments back (+z), the last a bone blade with a spike.
		CubeListBuilder tailBox = CubeListBuilder.create().texOffs(124, 52).addBox(-2.5f, -2.5f, 0, 5, 5, 10);
		PartDefinition segment = body.addOrReplaceChild("tail1", tailBox, PartPose.offsetAndRotation(0, -1, 15, -0.1f, 0, 0));
		for (int i = 2; i <= 4; i++) {
			segment = segment.addOrReplaceChild("tail" + i, tailBox, PartPose.offsetAndRotation(0, 0, 10, 0.08f, 0, 0));
		}
		segment.addOrReplaceChild("tail5", CubeListBuilder.create()
				.texOffs(124, 67).addBox(-0.5f, -3, 0, 1, 6, 10)
				.texOffs(204, 52).addBox(-1.5f, -1.5f, 0, 3, 3, 6),
				PartPose.offsetAndRotation(0, 0, 10, 0.08f, 0, 0));

		// Legs: thin, two segments and a clawed foot; 14 px tall so the feet stand on the ground (y 24).
		float[][] legPos = {{5, 4, -10}, {-5, 4, -10}, {5, 4, 10}, {-5, 4, 10}};
		for (int i = 0; i < LEGS.length; i++) {
			boolean left = legPos[i][0] > 0;
			CubeListBuilder upper = CubeListBuilder.create().texOffs(154, 52);
			CubeListBuilder lower = CubeListBuilder.create().texOffs(170, 52);
			CubeListBuilder foot = CubeListBuilder.create().texOffs(182, 52);
			if (!left) {
				upper = upper.mirror();
				lower = lower.mirror();
				foot = foot.mirror();
			}
			PartDefinition leg = body.addOrReplaceChild(LEGS[i], upper.addBox(-2, 0, -2, 4, 7, 4),
					PartPose.offset(legPos[i][0], legPos[i][1], legPos[i][2]));
			PartDefinition shin = leg.addOrReplaceChild(LEGS[i] + "_shin", lower.addBox(-1.5f, 0, -1.5f, 3, 7, 3), PartPose.offset(0, 7, 0));
			shin.addOrReplaceChild(LEGS[i] + "_foot", foot.addBox(-2.5f, 0, -4, 5, 2, 6), PartPose.offset(0, 5, 0));
		}
		return LayerDefinition.create(mesh, 256, 128);
	}

	@Override
	protected void idle(BossRenderState state) {
		FinalDragonRenderState s = state instanceof FinalDragonRenderState f ? f : null;
		FinalDragon.Mode mode = s == null ? FinalDragon.Mode.FLYING : s.mode;
		float t = state.ageInTicks;
		boolean onGround = mode == FinalDragon.Mode.PERCHED;
		// Body pitch follows its flight (dives nose-down, climbs nose-up).
		body.xRot = onGround ? Mth.sin(t * 0.08f) * 0.02f : Mth.clamp(state.xRot, -40, 40) * Mth.DEG_TO_RAD * 0.8f;
		// The neck turns toward what it looks at.
		float look = Mth.clamp(state.yRot, -60, 60) * Mth.DEG_TO_RAD;
		neck1.yRot = look * 0.3f;
		neck2.yRot = look * 0.3f;
		neck3.yRot = look * 0.3f;
		neck2.xRot += Mth.sin(t * 0.07f) * 0.04f;
		jaw.xRot = 0.05f + Mth.sin(t * 0.05f) * 0.03f;

		if (onGround) {
			// Wings folded along the flanks, tail curled on the ground, legs planted.
			leftWing.zRot = -0.35f;
			leftWing.yRot = -1.25f;
			leftWingTip.zRot = 2.6f;
			rightWing.zRot = 0.35f;
			rightWing.yRot = 1.25f;
			rightWingTip.zRot = -2.6f;
			for (int i = 0; i < legs.length; i++) {
				legs[i].xRot = 0;
				shins[i].xRot = 0;
			}
			for (int i = 0; i < tail.length; i++) {
				tail[i].yRot = Mth.sin(t * 0.04f + i * 0.6f) * 0.08f + 0.12f;
			}
			return;
		}
		// Flight: slow heavy flaps (faster when pulled down, frantic).
		float speed = mode == FinalDragon.Mode.PULLED ? 0.45f : 0.16f;
		float flap = Mth.sin(t * speed);
		leftWing.zRot = flap * 0.55f - 0.1f;
		rightWing.zRot = -flap * 0.55f + 0.1f;
		float tip = Mth.sin(t * speed - 0.9f);
		leftWingTip.zRot = tip * 0.45f + 0.1f;
		rightWingTip.zRot = -tip * 0.45f - 0.1f;
		// The body rises as the wings come down.
		body.y += -flap * 1.5f;
		for (int i = 0; i < legs.length; i++) {
			// Legs tucked back.
			legs[i].xRot = 1.1f;
			shins[i].xRot = 0.6f;
		}
		for (int i = 0; i < tail.length; i++) {
			tail[i].yRot = Mth.sin(t * 0.09f - i * 0.7f) * 0.12f;
			tail[i].xRot += Mth.sin(t * speed - i * 0.5f) * 0.05f;
		}
	}

	private static float seconds(int ticks) {
		return Math.max(0.05f, ticks / 20f);
	}

	private static FinalRaidConfig config() {
		return FinalDragon.config();
	}

	@Override
	protected @Nullable AnimationDefinition animation(@Nullable String attack, Stage stage) {
		if (attack == null) {
			return stage == Stage.STAGGER ? roar(seconds(BossEntity.framework().staggerTicks)) : null;
		}
		return switch (attack) {
			case "breath_line" -> breath(config().breathLine, stage);
			case "breath_sweep" -> breath(config().breathSweep, stage);
			case "dive" -> dive(config().dive, stage);
			case "void_orbs" -> orbs(config().voidOrbs, stage);
			case "tail_sweep" -> tailSweep(config().tailSweep, stage);
			case "wing_gust" -> gust(config().wingGust, stage);
			case "meteor_shower" -> meteors(config().meteorShower, stage);
			default -> null;
		};
	}

	/** Phase change: rears up, jaw wide, wings flared. */
	private static AnimationDefinition roar(float s) {
		return Anim.of(s)
				.rot("neck1", 0, 0, 0, 0, s * 0.3f, -35, 0, 0, s * 0.8f, -35, 0, 0, s, 0, 0, 0)
				.rot("head", 0, 0, 0, 0, s * 0.3f, -25, 0, 0, s * 0.8f, -25, 0, 0, s, 0, 0, 0)
				.rot("jaw", 0, 0, 0, 0, s * 0.3f, 40, 0, 0, s * 0.8f, 40, 0, 0, s, 0, 0, 0)
				.rot("left_wing", 0, 0, 0, 0, s * 0.3f, 0, 0, -30, s * 0.8f, 0, 0, -30, s, 0, 0, 0)
				.rot("right_wing", 0, 0, 0, 0, s * 0.3f, 0, 0, 30, s * 0.8f, 0, 0, 30, s, 0, 0, 0)
				.build();
	}

	/** Breath: draws its head back with the jaw parting, then pours, jaw wide and head low; closes as it climbs away. */
	private static @Nullable AnimationDefinition breath(AttackStats a, Stage stage) {
		float w = seconds(a.windupTicks);
		float act = seconds(a.activeTicks);
		float r = seconds(a.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("neck1", 0, 0, 0, 0, w, -25, 0, 0)
					.rot("head", 0, 0, 0, 0, w, -15, 0, 0)
					.rot("jaw", 0, 0, 0, 0, w, 25, 0, 0)
					.build();
			case ACTIVE -> Anim.of(act)
					.rot("neck1", 0, -25, 0, 0, act * 0.15f, 25, 0, 0, act, 25, 0, 0)
					.rot("head", 0, -15, 0, 0, act * 0.15f, 15, 0, 0, act, 15, 0, 0)
					.rot("jaw", 0, 25, 0, 0, act * 0.15f, 45, 0, 0, act, 45, 0, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("neck1", 0, 25, 0, 0, r, 0, 0, 0)
					.rot("head", 0, 15, 0, 0, r, 0, 0, 0)
					.rot("jaw", 0, 45, 0, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Dive: wings high and wide through the screech, then swept back like a falling blade. */
	private static @Nullable AnimationDefinition dive(AttackStats a, Stage stage) {
		float w = seconds(a.windupTicks);
		float act = seconds(a.activeTicks);
		float r = seconds(a.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("left_wing", 0, 0, 0, 0, w, 0, 0, -45)
					.rot("right_wing", 0, 0, 0, 0, w, 0, 0, 45)
					.rot("neck1", 0, 0, 0, 0, w * 0.5f, -30, 0, 0, w, -20, 0, 0)
					.rot("jaw", 0, 0, 0, 0, w * 0.5f, 40, 0, 0, w, 20, 0, 0)
					.build();
			case ACTIVE -> Anim.of(act)
					.rot("left_wing", 0, 0, 0, -45, act * 0.3f, 0, -65, -10, act, 0, -65, -10)
					.rot("right_wing", 0, 0, 0, 45, act * 0.3f, 0, 65, 10, act, 0, 65, 10)
					.rot("neck1", 0, -20, 0, 0, act * 0.3f, 15, 0, 0, act, 15, 0, 0)
					.rot("jaw", 0, 20, 0, 0, act * 0.3f, 35, 0, 0, act, 35, 0, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("left_wing", 0, 0, -65, -10, r, 0, 0, 0)
					.rot("right_wing", 0, 0, 65, 10, r, 0, 0, 0)
					.rot("neck1", 0, 15, 0, 0, r, 0, 0, 0)
					.rot("jaw", 0, 35, 0, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Void orbs: rears its head with the jaw slowly opening, then snaps forward. */
	private static @Nullable AnimationDefinition orbs(AttackStats a, Stage stage) {
		float w = seconds(a.windupTicks);
		float act = seconds(a.activeTicks);
		float r = seconds(a.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("neck1", 0, 0, 0, 0, w, -40, 0, 0)
					.rot("head", 0, 0, 0, 0, w, -10, 0, 0)
					.rot("jaw", 0, 0, 0, 0, w, 35, 0, 0)
					.build();
			case ACTIVE -> Anim.of(act)
					.rot("neck1", 0, -40, 0, 0, act, 20, 0, 0)
					.rot("head", 0, -10, 0, 0, act, 10, 0, 0)
					.rot("jaw", 0, 35, 0, 0, act, 45, 0, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("neck1", 0, 20, 0, 0, r, 0, 0, 0)
					.rot("head", 0, 10, 0, 0, r, 0, 0, 0)
					.rot("jaw", 0, 45, 0, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Tail sweep: the tail coils around to one side, then whips all the way across. */
	private static @Nullable AnimationDefinition tailSweep(AttackStats a, Stage stage) {
		float w = seconds(a.windupTicks);
		float act = seconds(a.activeTicks);
		float r = seconds(a.recoveryTicks);
		Anim anim;
		switch (stage) {
			case WINDUP -> {
				anim = Anim.of(w).rot("body", 0, 0, 0, 0, w, 0, -12, 0);
				for (int i = 1; i <= 5; i++) {
					anim.rot("tail" + i, 0, 0, 0, 0, w, 0, 22, 0);
				}
			}
			case ACTIVE -> {
				anim = Anim.of(act).rot("body", 0, 0, -12, 0, act, 0, 14, 0);
				for (int i = 1; i <= 5; i++) {
					anim.rot("tail" + i, 0, 0, 22, 0, act, 0, -26, 0);
				}
			}
			case RECOVERY -> {
				anim = Anim.of(r).rot("body", 0, 0, 14, 0, r, 0, 0, 0);
				for (int i = 1; i <= 5; i++) {
					anim.rot("tail" + i, 0, 0, -26, 0, r, 0, 0, 0);
				}
			}
			default -> {
				return null;
			}
		}
		return anim.build();
	}

	/** Wing gust: the folded wings rise and open forward, then beat down once. */
	private static @Nullable AnimationDefinition gust(AttackStats a, Stage stage) {
		float w = seconds(a.windupTicks);
		float act = seconds(a.activeTicks);
		float r = seconds(a.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("left_wing", 0, 0, 0, 0, w, 0, 80, -50)
					.rot("right_wing", 0, 0, 0, 0, w, 0, -80, 50)
					.rot("left_wing_tip", 0, 0, 0, 0, w, 0, 0, -130)
					.rot("right_wing_tip", 0, 0, 0, 0, w, 0, 0, 130)
					.rot("neck1", 0, 0, 0, 0, w, -20, 0, 0)
					.build();
			case ACTIVE -> Anim.of(act)
					.rot("left_wing", 0, 0, 80, -50, act, 0, 70, 35)
					.rot("right_wing", 0, 0, -80, 50, act, 0, -70, -35)
					.rot("left_wing_tip", 0, 0, 0, -130, act, 0, 0, -120)
					.rot("right_wing_tip", 0, 0, 0, 130, act, 0, 0, 120)
					.rot("neck1", 0, -20, 0, 0, act, 10, 0, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("left_wing", 0, 0, 70, 35, r, 0, 0, 0)
					.rot("right_wing", 0, 0, -70, -35, r, 0, 0, 0)
					.rot("left_wing_tip", 0, 0, 0, -120, r, 0, 0, 0)
					.rot("right_wing_tip", 0, 0, 0, 120, r, 0, 0, 0)
					.rot("neck1", 0, 10, 0, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Meteor shower: head thrown back to the sky, jaw wide, wings flared. */
	private static @Nullable AnimationDefinition meteors(AttackStats a, Stage stage) {
		float w = seconds(a.windupTicks);
		float act = seconds(a.activeTicks);
		float r = seconds(a.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("neck1", 0, 0, 0, 0, w, -50, 0, 0)
					.rot("head", 0, 0, 0, 0, w, -30, 0, 0)
					.rot("jaw", 0, 0, 0, 0, w, 45, 0, 0)
					.build();
			case ACTIVE -> Anim.of(act)
					.rot("neck1", 0, -50, 0, 0, act, -50, 0, 0)
					.rot("head", 0, -30, 0, 0, act, -30, 0, 0)
					.rot("jaw", 0, 45, 0, 0, act, 45, 0, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("neck1", 0, -50, 0, 0, r, 0, 0, 0)
					.rot("head", 0, -30, 0, 0, r, 0, 0, 0)
					.rot("jaw", 0, 45, 0, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}
}
