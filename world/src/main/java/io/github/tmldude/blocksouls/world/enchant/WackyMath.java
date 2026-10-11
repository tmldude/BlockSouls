package io.github.tmldude.blocksouls.world.enchant;

/** Pure math behind the wacky enchantments (no Minecraft classes, so it's unit-tested). Vectors are double[3]. */
public final class WackyMath {
	private WackyMath() {
	}

	/**
	 * Bounce: mirrors the velocity's component along the hit face's axis and scales the result by {@code damping}.
	 * {@code axis} is 0 (x), 1 (y) or 2 (z).
	 */
	public static double[] reflect(double[] velocity, int axis, double damping) {
		double[] out = {velocity[0] * damping, velocity[1] * damping, velocity[2] * damping};
		out[axis] = -out[axis];
		return out;
	}

	/**
	 * Grapple: launch velocity from {@code from} toward {@code to}. Full {@code speed} beyond 10 blocks, scaled down
	 * (to a minimum of 40%) when closer so short pulls don't fling you past the target, with a small upward lift so
	 * you clear ledges. Returns null when out of {@code range}.
	 */
	public static double[] grapplePull(double[] from, double[] to, double speed, double range) {
		double dx = to[0] - from[0];
		double dy = to[1] - from[1];
		double dz = to[2] - from[2];
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist > range || dist < 1.0E-3) {
			return null;
		}
		double scale = speed * Math.max(0.4, Math.min(1.0, dist / 10.0)) / dist;
		return new double[] {dx * scale, dy * scale + 0.25, dz * scale};
	}

	/** Magnetized pull radius for a level (0 = no magnet). */
	public static double magnetRadius(int level, double level1, double level2) {
		return level <= 0 ? 0.0 : level == 1 ? level1 : level2;
	}

	/** Velocity nudging a drop at {@code item} toward {@code player}, capped at {@code speed}. */
	public static double[] magnetPull(double[] item, double[] player, double speed) {
		double dx = player[0] - item[0];
		double dy = player[1] - item[1];
		double dz = player[2] - item[2];
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist < 0.5) {
			return new double[] {0, 0, 0};
		}
		double s = Math.min(speed, dist) / dist;
		return new double[] {dx * s, dy * s, dz * s};
	}

	public static float vampiricHeal(int level, float perLevel) {
		return Math.max(0, level) * perLevel;
	}
}
