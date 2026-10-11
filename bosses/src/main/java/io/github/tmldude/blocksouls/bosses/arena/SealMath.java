package io.github.tmldude.blocksouls.bosses.arena;

/**
 * Pure geometry and rules of the arena seal (no Minecraft classes, unit-tested). The seal is a vertical cylinder
 * around the boss's home: radius = the fog wall ring, height = {@code sealHeight} up and down (≤ 0: unlimited).
 */
public final class SealMath {
	/** What the seal does about one player's move this tick. */
	public enum Verdict {
		/** Nothing to do. */
		ALLOW,
		/** A participant tried to leave: put them back inside. */
		KEEP_IN,
		/** An outsider tried to enter: put them back outside. */
		KEEP_OUT
	}

	private SealMath() {
	}

	/** Whether an offset from the arena center (feet to home block center) is inside the cylinder. */
	public static boolean inside(double dx, double dy, double dz, double radius, double height) {
		if (height > 0 && Math.abs(dy) > height) {
			return false;
		}
		return dx * dx + dz * dz <= radius * radius;
	}

	/**
	 * The rule: participants may come in but never leave; everyone else may leave but never come in. A move longer
	 * than {@code maxMove} in one tick is a server teleport (commands, run transitions), never stopped.
	 */
	public static Verdict judge(boolean wasInside, boolean isInside, boolean participant, double moved, double maxMove) {
		if (wasInside == isInside || moved > maxMove) {
			return Verdict.ALLOW;
		}
		if (wasInside) {
			return participant ? Verdict.KEEP_IN : Verdict.ALLOW;
		}
		return participant ? Verdict.ALLOW : Verdict.KEEP_OUT;
	}

	/** A web rope (or any straight tether) crosses the seal when its two ends are on different sides. */
	public static boolean crosses(boolean aInside, boolean bInside) {
		return aInside != bInside;
	}

	/**
	 * Where (0..1) the horizontal segment from (ax, az) to (bx, bz) (offsets from the center) meets the circle of
	 * {@code radius}; 0.5 if it doesn't (e.g. a crossing through the top or bottom of the cylinder).
	 */
	public static double crossingFraction(double ax, double az, double bx, double bz, double radius) {
		double dx = bx - ax;
		double dz = bz - az;
		double a = dx * dx + dz * dz;
		if (a < 1e-9) {
			return 0.5;
		}
		double b = 2 * (ax * dx + az * dz);
		double c = ax * ax + az * az - radius * radius;
		double disc = b * b - 4 * a * c;
		if (disc < 0) {
			return 0.5;
		}
		double sqrt = Math.sqrt(disc);
		double t1 = (-b - sqrt) / (2 * a);
		double t2 = (-b + sqrt) / (2 * a);
		if (t1 >= 0 && t1 <= 1) {
			return t1;
		}
		if (t2 >= 0 && t2 <= 1) {
			return t2;
		}
		return 0.5;
	}

	/**
	 * Horizontal push (x, z) for a bounce: toward the center to keep someone in, away from it to keep someone out.
	 * {@code (dx, dz)} is the player's offset from the center.
	 */
	public static double[] push(double dx, double dz, boolean inward, double strength) {
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1e-6) {
			return new double[] {0, 0};
		}
		double sign = inward ? -1 : 1;
		return new double[] {sign * dx / length * strength, sign * dz / length * strength};
	}
}
