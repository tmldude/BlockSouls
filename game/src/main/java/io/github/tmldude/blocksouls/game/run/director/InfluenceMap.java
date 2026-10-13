package io.github.tmldude.blocksouls.game.run.director;

/**
 * A coarse influence map of player pressure around the party (PLAN2 §3b): the compass around the party's
 * centre split into sectors, each holding how much fire already points that way (players' facing, recent
 * kills). Spawns prefer the quiet sectors, so hordes flank and surround instead of conga-lining into one
 * gun lane. Pure; angles use the spawner's convention ({@code x = cos(a)}, {@code z = sin(a)}).
 */
public final class InfluenceMap {
	private final double[] pressure;

	public InfluenceMap(int sectors) {
		pressure = new double[Math.max(1, sectors)];
	}

	public int sectors() {
		return pressure.length;
	}

	public double pressure(int sector) {
		return pressure[Math.floorMod(sector, pressure.length)];
	}

	/** The sector holding the angle (radians). */
	public int sectorOf(double angle) {
		double turn = Math.PI * 2;
		double normalized = ((angle % turn) + turn) % turn;
		return Math.min(pressure.length - 1, (int) (normalized / turn * pressure.length));
	}

	/** The middle of a sector (radians). */
	public double centreOf(int sector) {
		return (Math.floorMod(sector, pressure.length) + 0.5) / pressure.length * Math.PI * 2;
	}

	/** Adds pressure toward {@code angle}, and {@code spread}× of it to the two neighbouring sectors. */
	public void add(double angle, double amount, double spread) {
		int sector = sectorOf(angle);
		pressure[sector] += amount;
		if (pressure.length > 2) {
			pressure[Math.floorMod(sector - 1, pressure.length)] += amount * spread;
			pressure[Math.floorMod(sector + 1, pressure.length)] += amount * spread;
		}
	}

	/** Pressure toward the direction {@code (dx, dz)} (e.g. a player's look vector or a kill relative to the centre). */
	public void addDirection(double dx, double dz, double amount, double spread) {
		if (dx == 0 && dz == 0) {
			return;
		}
		add(Math.atan2(dz, dx), amount, spread);
	}

	public void decay(double keep) {
		double factor = Math.max(0, Math.min(1, keep));
		for (int i = 0; i < pressure.length; i++) {
			pressure[i] *= factor;
		}
	}

	public void clear() {
		java.util.Arrays.fill(pressure, 0);
	}

	/** The least-pressured sector (lowest index on ties). */
	public int quietest() {
		int best = 0;
		for (int i = 1; i < pressure.length; i++) {
			if (pressure[i] < pressure[best]) {
				best = i;
			}
		}
		return best;
	}

	/**
	 * Picks a sector with weight {@code exp(-sharpness · pressure / max)}: quiet sectors win most of the time,
	 * pressured ones still get the odd mob. {@code roll} in [0, 1).
	 */
	public int pick(double roll, double sharpness) {
		double max = 0;
		for (double value : pressure) {
			max = Math.max(max, value);
		}
		double[] weights = new double[pressure.length];
		double total = 0;
		for (int i = 0; i < pressure.length; i++) {
			weights[i] = Math.exp(-Math.max(0, sharpness) * (max <= 0 ? 0 : pressure[i] / max));
			total += weights[i];
		}
		double target = Math.max(0, Math.min(roll, 0.999999)) * total;
		for (int i = 0; i < weights.length; i++) {
			target -= weights[i];
			if (target < 0) {
				return i;
			}
		}
		return weights.length - 1;
	}

	/**
	 * The flank: the sector opposite the most-pressured one (behind the party's guns). With no pressure at all,
	 * sector 0.
	 */
	public int flank() {
		int hottest = 0;
		for (int i = 1; i < pressure.length; i++) {
			if (pressure[i] > pressure[hottest]) {
				hottest = i;
			}
		}
		return Math.floorMod(hottest + pressure.length / 2, pressure.length);
	}

	/** True when any pressure is recorded. */
	public boolean hasPressure() {
		for (double value : pressure) {
			if (value > 1e-9) {
				return true;
			}
		}
		return false;
	}

	/** A player's look direction from their yaw (degrees, Minecraft convention) as an angle in the map's frame. */
	public static double yawToAngle(float yawDegrees) {
		double yaw = Math.toRadians(yawDegrees);
		return Math.atan2(Math.cos(yaw), -Math.sin(yaw));
	}
}
