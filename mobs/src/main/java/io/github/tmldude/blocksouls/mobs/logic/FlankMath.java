package io.github.tmldude.blocksouls.mobs.logic;

/**
 * Geometry for flanking (pure, horizontal plane, Minecraft yaw: 0 = +Z, 90 = -X). A "bearing" is the angle of a point
 * around the target, measured from where the target faces: 0 = straight in front, ±180 = straight behind.
 */
public final class FlankMath {
	private FlankMath() {
	}

	/** The yaw (degrees) looking from the origin along (dx, dz). */
	public static double yaw(double dx, double dz) {
		return Math.toDegrees(Math.atan2(-dx, dz));
	}

	/** Wraps degrees to (-180, 180]. */
	public static double wrap(double degrees) {
		double d = degrees % 360;
		if (d > 180) {
			d -= 360;
		} else if (d <= -180) {
			d += 360;
		}
		return d;
	}

	/** Bearing of the point (dx, dz) from the target, relative to the target's facing yaw. */
	public static double bearing(double facingYaw, double dx, double dz) {
		return wrap(yaw(dx, dz) - facingYaw);
	}

	/** Behind = the bearing is at least {@code behindDegrees} off the facing, either side. */
	public static boolean behind(double bearing, double behindDegrees) {
		return Math.abs(bearing) >= behindDegrees;
	}

	/**
	 * The next bearing to head for: a {@code step}-degree move around the target away from its facing, on whichever side
	 * the mob already is (so it never cuts across the front), never past straight behind.
	 */
	public static double nextBearing(double bearing, double step) {
		double side = bearing >= 0 ? 1 : -1;
		double next = Math.abs(bearing) + step;
		return side * Math.min(180, next);
	}

	/** Offset (x, z) from the target of a point at {@code bearing} and {@code radius}. */
	public static double[] offset(double facingYaw, double bearing, double radius) {
		double yaw = Math.toRadians(facingYaw + bearing);
		return new double[] {-Math.sin(yaw) * radius, Math.cos(yaw) * radius};
	}

	/**
	 * Mean facing yaw of several players (circular mean). Returns {@code fallback} for none, or when the facings cancel out.
	 */
	public static double meanYaw(double[] yaws, double fallback) {
		double x = 0;
		double z = 0;
		for (double yaw : yaws) {
			x += -Math.sin(Math.toRadians(yaw));
			z += Math.cos(Math.toRadians(yaw));
		}
		if (x * x + z * z < 1e-6) {
			return fallback;
		}
		return yaw(x, z);
	}

	/** Whether a hit coming from bearing {@code bearing} (relative to the defender's facing) lands in its front arc. */
	public static boolean inFrontArc(double bearing, double arcDegrees) {
		return Math.abs(bearing) <= arcDegrees / 2;
	}
}
