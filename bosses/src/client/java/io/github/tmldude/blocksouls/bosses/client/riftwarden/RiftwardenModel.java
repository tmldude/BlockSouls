package io.github.tmldude.blocksouls.bosses.client.riftwarden;

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
import io.github.tmldude.blocksouls.bosses.riftwarden.RiftwardenConfig;
import io.github.tmldude.blocksouls.bosses.riftwarden.Riftwardens;

/**
 * Placeholder Riftwarden: a tall horned warden in an obsidian shell and a long robe, crystal blades jutting from both
 * forearms, a rift core floating in front of the chest. Drawn at 1.3× so it stands about 4 blocks tall. Box UVs are
 * mirrored in {@code tools/assets/riftwarden.py}.
 */
public class RiftwardenModel extends BossModel {
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart core;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public RiftwardenModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		head = body.getChild("head");
		core = body.getChild("core");
		rightArm = body.getChild("right_arm");
		leftArm = body.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-8, -22, -5, 16, 22, 10)
				.texOffs(0, 70).addBox(-9, -2, -5.5f, 18, 14, 11),
				PartPose.offset(0, 2, 0));
		body.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(60, 0).addBox(-5, -10, -5, 10, 10, 10)
				.texOffs(104, 0).addBox(-5, -16, -1, 2, 8, 2)
				.texOffs(104, 0).mirror().addBox(3, -16, -1, 2, 8, 2),
				PartPose.offset(0, -22, 0));
		body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(104, 16).addBox(-3, -3, -3, 6, 6, 6),
				PartPose.offset(0, -13, -10));
		PartDefinition rightArm = body.addOrReplaceChild("right_arm", CubeListBuilder.create()
				.texOffs(0, 36).addBox(-3, -2, -3, 6, 24, 6),
				PartPose.offset(-11, -19, 0));
		rightArm.addOrReplaceChild("right_blade", CubeListBuilder.create().texOffs(28, 36).addBox(-1, -2, -18, 2, 4, 18),
				PartPose.offset(0, 20, 0));
		PartDefinition leftArm = body.addOrReplaceChild("left_arm", CubeListBuilder.create()
				.texOffs(0, 36).mirror().addBox(-3, -2, -3, 6, 24, 6),
				PartPose.offset(11, -19, 0));
		leftArm.addOrReplaceChild("left_blade", CubeListBuilder.create().texOffs(28, 36).mirror().addBox(-1, -2, -18, 2, 4, 18),
				PartPose.offset(0, 20, 0));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(76, 26).addBox(-3, 0, -3.5f, 6, 22, 7),
				PartPose.offset(-4, 2, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(76, 26).mirror().addBox(-3, 0, -3.5f, 6, 22, 7),
				PartPose.offset(4, 2, 0));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	protected void idle(BossRenderState state) {
		float pos = state.walkAnimationPos;
		float speed = Math.min(1f, state.walkAnimationSpeed);
		rightLeg.xRot = Mth.cos(pos * 0.45f) * 0.6f * speed;
		leftLeg.xRot = Mth.cos(pos * 0.45f + Mth.PI) * 0.6f * speed;
		rightArm.xRot = -0.25f + Mth.cos(pos * 0.45f + Mth.PI) * 0.2f * speed;
		leftArm.xRot = -0.25f + Mth.cos(pos * 0.45f) * 0.2f * speed;
		rightArm.zRot = 0.12f;
		leftArm.zRot = -0.12f;
		body.xRot = 0.05f + Mth.sin(state.ageInTicks * 0.05f) * 0.02f;
		// The core bobs and turns; it spins faster once transformed.
		core.y += Mth.sin(state.ageInTicks * 0.1f) * 1.2f;
		core.yRot = state.ageInTicks * (state.phase >= 1 ? 0.2f : 0.06f);
		head.yRot = Mth.clamp(state.yRot, -40, 40) * Mth.DEG_TO_RAD * 0.5f;
		head.xRot = Mth.clamp(state.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.4f;
	}

	private static float seconds(int ticks) {
		return Math.max(0.05f, ticks / 20f);
	}

	private static RiftwardenConfig config() {
		return Riftwardens.CONFIG;
	}

	@Override
	protected @Nullable AnimationDefinition animation(@Nullable String attack, Stage stage) {
		if (attack == null) {
			return stage == Stage.STAGGER ? stagger(seconds(BossEntity.framework().staggerTicks)) : null;
		}
		return switch (attack) {
			case "ring", "summon", "orbs" -> raise(stats(attack), stage);
			case "cross", "beam", "feint" -> thrust(stats(attack), stage);
			case "leap", "charge" -> slam(stats(attack), stage);
			default -> null;
		};
	}

	private static AttackStats stats(String attack) {
		RiftwardenConfig c = config();
		return switch (attack) {
			case "ring" -> c.ring.stats;
			case "cross" -> c.cross.stats;
			case "leap" -> c.leap.stats;
			case "charge" -> c.charge.stats;
			case "beam" -> c.beam.stats;
			case "orbs" -> c.orbs.stats;
			case "summon" -> c.summon.stats;
			default -> c.feint.stats;
		};
	}

	/** Both arms rise overhead through the windup, then sweep down and out. */
	private static @Nullable AnimationDefinition raise(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("right_arm", 0, 0, 0, 0, w, -165, 0, 20)
					.rot("left_arm", 0, 0, 0, 0, w, -165, 0, -20)
					.rot("body", 0, 0, 0, 0, w, -10, 0, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("right_arm", 0, -165, 0, 20, Math.min(a, 0.3f), -20, 0, 70)
					.rot("left_arm", 0, -165, 0, -20, Math.min(a, 0.3f), -20, 0, -70)
					.rot("body", 0, -10, 0, 0, Math.min(a, 0.3f), 12, 0, 0)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("right_arm", 0, -20, 0, 70, r, 0, 0, 0)
					.rot("left_arm", 0, -20, 0, -70, r, 0, 0, 0)
					.rot("body", 0, 12, 0, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** The right blade draws back across the body, then drives straight out. */
	private static @Nullable AnimationDefinition thrust(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("body", 0, 0, 0, 0, w, 0, 30, 0)
					.rot("right_arm", 0, 0, 0, 0, w, -40, 0, 0)
					.pos("right_arm", 0, 0, 0, 0, w, 0, 0, 8)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("body", 0, 0, 30, 0, Math.min(a, 0.2f), 10, -15, 0)
					.rot("right_arm", 0, -40, 0, 0, Math.min(a, 0.2f), -90, 0, 0)
					.pos("right_arm", 0, 0, 0, 8, Math.min(a, 0.2f), 0, 0, -6)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("body", 0, 10, -15, 0, r, 0, 0, 0)
					.rot("right_arm", 0, -90, 0, 0, r, 0, 0, 0)
					.pos("right_arm", 0, 0, 0, -6, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Crouches with both blades back, then lunges and brings them down together. */
	private static @Nullable AnimationDefinition slam(AttackStats s, Stage stage) {
		float w = seconds(s.windupTicks);
		float a = seconds(s.activeTicks);
		float r = seconds(s.recoveryTicks);
		return switch (stage) {
			case WINDUP -> Anim.of(w)
					.rot("body", 0, 0, 0, 0, w, 25, 0, 0)
					.rot("right_arm", 0, 0, 0, 0, w, 40, 0, 15)
					.rot("left_arm", 0, 0, 0, 0, w, 40, 0, -15)
					.rot("right_leg", 0, 0, 0, 0, w, -30, 0, 0)
					.rot("left_leg", 0, 0, 0, 0, w, 20, 0, 0)
					.build();
			case ACTIVE -> Anim.of(a)
					.rot("body", 0, 25, 0, 0, a, 15, 0, 0)
					.rot("right_arm", 0, 40, 0, 15, Math.min(a, 0.3f), -150, 0, 10, a, -60, 0, 10)
					.rot("left_arm", 0, 40, 0, -15, Math.min(a, 0.3f), -150, 0, -10, a, -60, 0, -10)
					.build();
			case RECOVERY -> Anim.of(r)
					.rot("body", 0, 15, 0, 0, r, 0, 0, 0)
					.rot("right_arm", 0, -60, 0, 10, r, 0, 0, 0)
					.rot("left_arm", 0, -60, 0, -10, r, 0, 0, 0)
					.rot("right_leg", 0, -30, 0, 0, r, 0, 0, 0)
					.rot("left_leg", 0, 20, 0, 0, r, 0, 0, 0)
					.build();
			default -> null;
		};
	}

	/** Reels back, arms flung wide, core flaring. */
	private static AnimationDefinition stagger(float t) {
		return Anim.of(t)
				.rot("body", 0, 0, 0, 0, t * 0.3f, -25, 0, 0, t, 0, 0, 0)
				.rot("right_arm", 0, 0, 0, 0, t * 0.3f, -60, 0, 60, t, 0, 0, 0)
				.rot("left_arm", 0, 0, 0, 0, t * 0.3f, -60, 0, -60, t, 0, 0, 0)
				.build();
	}
}
