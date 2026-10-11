package io.github.tmldude.blocksouls.mobs.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.client.render.MobRenderState;
import io.github.tmldude.blocksouls.mobs.entity.Brute;
import io.github.tmldude.blocksouls.mobs.entity.MutantBrute;

/** The two brute models: windups come from each mob's own actions. */
public final class BruteAnimations {
	private BruteAnimations() {
	}

	public static class BruteSlam extends BruteModel {
		public BruteSlam(ModelPart root) {
			super(root);
		}

		@Override
		protected float windup(MobRenderState state) {
			return state.action == Brute.ACTION_SLAM ? Mth.clamp(state.actionTicks / BlockSoulsMobs.CONFIG.brute.slamWindupTicks, 0, 1) : 0;
		}
	}

	public static class Mutant extends BruteModel {
		private final ModelPart growth;

		public Mutant(ModelPart root) {
			super(root);
			growth = rightArm.getChild("growth");
		}

		@Override
		protected float windup(MobRenderState state) {
			return switch (state.action) {
				case MutantBrute.ACTION_LEAP_WINDUP -> Mth.clamp(state.actionTicks / BlockSoulsMobs.CONFIG.mutantBrute.leapWindupTicks, 0, 1);
				// Arms stay up through the jump, then come down on landing.
				case MutantBrute.ACTION_LEAPING -> 1;
				case MutantBrute.ACTION_THROW -> Mth.clamp(state.actionTicks / MutantBrute.THROW_WINDUP_TICKS, 0, 1);
				default -> 0;
			};
		}

		@Override
		public void setupAnim(MobRenderState state) {
			super.setupAnim(state);
			// The tumor on its shoulder throbs.
			float throb = 1 + Mth.sin((state.ageInTicks + state.phase) * 0.2f) * 0.06f;
			growth.xScale = throb;
			growth.yScale = throb;
			growth.zScale = throb;
			if (state.action == MutantBrute.ACTION_THROW) {
				// Only the right arm hauls the debris back, the body twisting into it; the left braces.
				float w = windup(state);
				leftArm.xRot = -0.9f;
				leftArm.zRot = -0.3f;
				body.yRot -= 0.4f * w;
			}
			if (state.action == MutantBrute.ACTION_LEAP_WINDUP) {
				// Crouch before the jump.
				body.y += 3 * windup(state);
				rightLeg.xRot -= 0.3f * windup(state);
				leftLeg.xRot -= 0.3f * windup(state);
			}
		}
	}
}
