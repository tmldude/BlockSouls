package io.github.tmldude.blocksouls.guns.logic;

/** Random bullet deflection inside a cone around the aim direction. */
public final class Spread {
	private Spread() {
	}

	/**
	 * Deflects the unit vector {@code (x, y, z)} by up to {@code maxDegrees}, spread evenly over the cone's disk.
	 *
	 * @param r1 uniform [0, 1): distance from the center
	 * @param r2 uniform [0, 1): angle around the center
	 * @return a unit vector {x, y, z}
	 */
	public static double[] deflect(double x, double y, double z, double maxDegrees, double r1, double r2) {
		double len = Math.sqrt(x * x + y * y + z * z);
		x /= len;
		y /= len;
		z /= len;
		if (maxDegrees <= 0) {
			return new double[] {x, y, z};
		}

		double angle = Math.toRadians(maxDegrees) * Math.sqrt(r1);
		double around = 2 * Math.PI * r2;

		// Any vector not parallel to the direction gives a basis perpendicular to it.
		double ax = Math.abs(y) < 0.99 ? 0 : 1;
		double ay = Math.abs(y) < 0.99 ? 1 : 0;
		// u = normalize(a × d), v = d × u
		double ux = ay * z;
		double uy = -ax * z;
		double uz = ax * y - ay * x;
		double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
		ux /= ul;
		uy /= ul;
		uz /= ul;
		double vx = y * uz - z * uy;
		double vy = z * ux - x * uz;
		double vz = x * uy - y * ux;

		double sin = Math.sin(angle);
		double cos = Math.cos(angle);
		double cu = Math.cos(around) * sin;
		double cv = Math.sin(around) * sin;
		return new double[] {
				x * cos + ux * cu + vx * cv,
				y * cos + uy * cu + vy * cv,
				z * cos + uz * cu + vz * cv
		};
	}

	/** Angle in degrees between two vectors. */
	public static double angleBetween(double[] a, double[] b) {
		double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
		double la = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
		double lb = Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]);
		return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot / (la * lb)))));
	}
}
