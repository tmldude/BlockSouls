package io.github.tmldude.blocksouls.bosses.logic;

/**
 * Which phase a boss should be in. Phase {@code i} starts once health drops below {@code thresholds[i]}
 * (a fraction of max health); phase 0's threshold is ignored. Phases only move forward, one at a time, so a
 * burst of damage that skips a threshold still plays every transition in order.
 */
public final class PhaseMath {
	private PhaseMath() {
	}

	/** The phase to move to from {@code current}: {@code current + 1} if its threshold is crossed, else {@code current}. */
	public static int next(int current, double healthFraction, double[] thresholds) {
		int next = current + 1;
		if (next < thresholds.length && healthFraction < thresholds[next]) {
			return next;
		}
		return current;
	}

	/** The phase a fresh boss at {@code healthFraction} settles in after every transition. */
	public static int settle(double healthFraction, double[] thresholds) {
		int phase = 0;
		while (true) {
			int next = next(phase, healthFraction, thresholds);
			if (next == phase) {
				return phase;
			}
			phase = next;
		}
	}

	/** Thresholds must start at 1 and strictly decrease, all within (0, 1]. */
	public static boolean valid(double[] thresholds) {
		if (thresholds.length == 0) {
			return false;
		}
		for (int i = 1; i < thresholds.length; i++) {
			if (!(thresholds[i] > 0 && thresholds[i] < thresholds[i - 1] && thresholds[i] <= 1)) {
				return false;
			}
		}
		return true;
	}
}
