package io.github.tmldude.blocksouls.bosses.logic;

import java.util.List;
import java.util.random.RandomGenerator;

/** Weighted random attack choice among the attacks that are off cooldown and in range. */
public final class AttackPicker {
	private AttackPicker() {
	}

	/**
	 * @param weight       relative pick weight ({@code <= 0} never picked)
	 * @param cooldownLeft ticks until it can be used again ({@code > 0} = not ready)
	 * @param minRange     closest distance to the target it can be used at
	 * @param maxRange     farthest distance it can be used at
	 */
	public record Option(double weight, int cooldownLeft, double minRange, double maxRange) {
		public boolean usable(double distance) {
			return weight > 0 && cooldownLeft <= 0 && distance >= minRange && distance <= maxRange;
		}
	}

	/**
	 * Picks an index into {@code options}, or -1 when none is usable at this distance.
	 *
	 * @param last          index of the previous attack (-1 for none); its weight is multiplied by
	 *                      {@code repeatPenalty} so the same move rarely comes twice in a row
	 * @param repeatPenalty 0..1
	 */
	public static int pick(List<Option> options, double distance, int last, double repeatPenalty, RandomGenerator random) {
		double total = 0;
		for (int i = 0; i < options.size(); i++) {
			total += effectiveWeight(options, i, distance, last, repeatPenalty);
		}
		if (total <= 0) {
			return -1;
		}
		double roll = random.nextDouble() * total;
		int fallback = -1;
		for (int i = 0; i < options.size(); i++) {
			double weight = effectiveWeight(options, i, distance, last, repeatPenalty);
			if (weight <= 0) {
				continue;
			}
			fallback = i;
			roll -= weight;
			if (roll < 0) {
				return i;
			}
		}
		// Rounding left a sliver; take the last usable one.
		return fallback;
	}

	private static double effectiveWeight(List<Option> options, int i, double distance, int last, double repeatPenalty) {
		Option option = options.get(i);
		if (!option.usable(distance)) {
			return 0;
		}
		return i == last ? option.weight() * Math.clamp(repeatPenalty, 0, 1) : option.weight();
	}

	/** True when at least one option could be used at some distance once cooldowns run out. */
	public static boolean anyInRange(List<Option> options, double distance) {
		for (Option option : options) {
			if (option.weight() > 0 && distance >= option.minRange() && distance <= option.maxRange()) {
				return true;
			}
		}
		return false;
	}
}
