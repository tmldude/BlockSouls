package io.github.tmldude.blocksouls.bosses.api;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * One phase of a fight: starts when health drops below {@link #threshold()} (the first phase uses 1.0), with its own
 * attack list and multipliers. Entering any phase after the first plays a stagger (an invulnerable roar), then
 * {@link #onEnter()} runs.
 *
 * @param damageMult   multiplies every hit's damage
 * @param cooldownMult multiplies attack cooldowns (0.7 = 30% shorter)
 * @param recoveryMult multiplies recovery windows (faster recovery = less punish time)
 * @param speedMult    multiplies the boss's chase speed
 */
public record BossPhase(double threshold, List<Attack> attacks, double damageMult, double cooldownMult, double recoveryMult,
		double speedMult, @Nullable Hook onEnter) {
	@FunctionalInterface
	public interface Hook {
		/** Server side, right after the stagger starts: change the arena, summon adds, buff the boss. */
		void onEnter(BossEntity boss);
	}

	public static Builder first() {
		return new Builder(1.0);
	}

	/** A phase that starts below {@code healthFraction} of max health (0.5 = below 50%). */
	public static Builder below(double healthFraction) {
		return new Builder(healthFraction);
	}

	public static final class Builder {
		private final double threshold;
		private final List<Attack> attacks = new ArrayList<>();
		private double damageMult = 1;
		private double cooldownMult = 1;
		private double recoveryMult = 1;
		private double speedMult = 1;
		private @Nullable Hook onEnter;

		private Builder(double threshold) {
			this.threshold = threshold;
		}

		public Builder attacks(Attack... attacks) {
			this.attacks.addAll(List.of(attacks));
			return this;
		}

		public Builder damage(double mult) {
			this.damageMult = mult;
			return this;
		}

		public Builder cooldowns(double mult) {
			this.cooldownMult = mult;
			return this;
		}

		public Builder recovery(double mult) {
			this.recoveryMult = mult;
			return this;
		}

		public Builder speed(double mult) {
			this.speedMult = mult;
			return this;
		}

		public Builder onEnter(Hook hook) {
			this.onEnter = hook;
			return this;
		}

		public BossPhase build() {
			return new BossPhase(threshold, List.copyOf(attacks), damageMult, cooldownMult, recoveryMult, speedMult, onEnter);
		}
	}
}
