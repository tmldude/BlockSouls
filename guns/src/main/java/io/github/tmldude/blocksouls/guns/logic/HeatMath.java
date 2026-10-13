package io.github.tmldude.blocksouls.guns.logic;

/** Pure heat math for the Kindling Engine: lazy decay, ramping fire rate, overheating. Heat is 0..{@link #MAX}. */
public final class HeatMath {
	public static final double MAX = 100;

	private HeatMath() {
	}

	/** Heat after {@code elapsedTicks} of cooling. */
	public static double decayed(double heat, long elapsedTicks, double decayPerTick) {
		return Math.clamp(heat - Math.max(0, elapsedTicks) * Math.max(0, decayPerTick), 0.0, MAX);
	}

	/** Heat after a hit; never above {@link #MAX}. */
	public static double heated(double heat, double amount) {
		return Math.clamp(heat + Math.max(0, amount), 0.0, MAX);
	}

	/** Whether a hit at {@code heat} tips the gun over the top. */
	public static boolean overheats(double heat, double amount) {
		return heat + Math.max(0, amount) >= MAX;
	}

	/** Fire-rate multiplier for this heat: 1 cold, {@code maxMult} at full heat (never below 1). */
	public static double rateMult(double heat, double maxMult) {
		return 1.0 + (Math.max(1.0, maxMult) - 1.0) * Math.clamp(heat / MAX, 0.0, 1.0);
	}
}
