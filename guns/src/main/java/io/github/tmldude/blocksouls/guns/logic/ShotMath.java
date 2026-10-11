package io.github.tmldude.blocksouls.guns.logic;

/** Pure gun math: damage, fire rate, falloff, reload time and precision hits. */
public final class ShotMath {
	private ShotMath() {
	}

	/** Ticks between shots for a rounds-per-minute rate (20 ticks/s). */
	public static double fireIntervalTicks(double rpm) {
		return 1200.0 / rpm;
	}

	/**
	 * Damage multiplier for distance: 1.0 up to {@code start}, then linear down to {@code minMult} at
	 * {@code range}. {@code start >= range} disables falloff.
	 */
	public static double falloff(double distance, double start, double range, double minMult) {
		if (distance <= start || start >= range) {
			return 1.0;
		}
		double t = Math.min(1.0, (distance - start) / (range - start));
		return 1.0 + (minMult - 1.0) * t;
	}

	/**
	 * Damage of one bullet / pellet.
	 *
	 * @param perkMult product of perk multipliers (Rampage, Vorpal); 1.0 for none
	 * @param bounces  ricochets before this hit; each multiplies by {@code bounceMult}
	 */
	public static double damage(double base, double rarityMult, boolean precision, double precisionMult, double perkMult,
			double falloffMult, int bounces, double bounceMult) {
		return base * rarityMult * (precision ? precisionMult : 1.0) * perkMult * falloffMult * Math.pow(bounceMult, bounces);
	}

	/** A hit counts as precision when it lands in the top of the target's body, scaled to its height. */
	public static boolean isPrecision(double hitY, double eyeY, double height) {
		return hitY >= eyeY - 0.3 * (height / 1.8);
	}

	/**
	 * Reload duration in ticks (at least 1). Each {@code RELOAD_SPEED} trait level adds {@code perLevel} to the
	 * reload speed; an active Outlaw multiplies the speed by {@code outlawSpeedMult}.
	 */
	public static int reloadTicks(double baseSeconds, int reloadSpeedLevel, double perLevel, boolean outlaw, double outlawSpeedMult) {
		double speed = (1.0 + Math.max(0, reloadSpeedLevel) * perLevel) * (outlaw ? outlawSpeedMult : 1.0);
		return Math.max(1, (int) Math.ceil(baseSeconds * 20.0 / speed));
	}
}
