package io.github.tmldude.blocksouls.world.floor;

import java.util.ArrayList;
import java.util.List;

/**
 * Infernal lava channels (pure geometry, no Minecraft classes, so the planner tests check it): a ring around the camp
 * plus radial cuts from the ring to near the edge, which split the island into lobes, and the bridges across them.
 * Angles in the template are in template space (degrees, 0 = +x, 90 = +z); {@link #plan} turns them with the floor's
 * rotation and mirroring, so the channels move with the slots. Coordinates are relative to the camp.
 */
public final class LavaChannels {
	/** The template's {@code channels} block. */
	public static class Spec {
		/** Ring radius as a share of the island radius. */
		public double ringFraction = 0.34;
		/** Channel width in blocks (lava surface). */
		public int width = 3;
		/** The lava surface sits this far below the island's ground level. */
		public int lavaDepth = 2;
		/** Radial channel angles (degrees, template space), from the ring out to {@link #edgeMargin} short of the edge. */
		public List<Double> radials = new ArrayList<>();
		/** Ring bridges (degrees, template space): one walkway across the ring at each angle. */
		public List<Double> ringBridges = new ArrayList<>();
		/** Index into {@link #ringBridges} of the high bridge (stone arch level with the ground: survives the rise). */
		public int highBridge = 0;
		/** Where a radial's bridge sits along it, as a share of the radial's length. */
		public double radialBridgeAt = 0.55;
		/** Radial bridges are stone arches like the high ring bridge (outer lobes stay linked after the rise). */
		public boolean radialBridgesHigh = true;
		/** Bridge walkway width. */
		public int bridgeWidth = 3;
		/** Radials stop this far inside the edge so the lava never reaches the rim. */
		public int edgeMargin = 5;

		public void validate() {
			if (width < 1 || width > 9) {
				throw new IllegalArgumentException("channels.width must be 1-9");
			}
			if (ringFraction <= 0.1 || ringFraction >= 0.8) {
				throw new IllegalArgumentException("channels.ringFraction must be between 0.1 and 0.8");
			}
			if (lavaDepth < 1) {
				throw new IllegalArgumentException("channels.lavaDepth must be at least 1");
			}
			if (radials == null) {
				radials = new ArrayList<>();
			}
			if (ringBridges == null || ringBridges.isEmpty()) {
				throw new IllegalArgumentException("channels need at least one ring bridge");
			}
		}
	}

	/** A bridge: centre, the unit axis it crosses along, half its length and half its width; high = arch at ground level. */
	public record Bridge(double x, double z, double ax, double az, double halfLength, double halfWidth, boolean high) {
		/** Whether a column lies on the walkway. */
		public boolean covers(double px, double pz) {
			double dx = px - x;
			double dz = pz - z;
			double along = dx * ax + dz * az;
			double across = -dx * az + dz * ax;
			return Math.abs(along) <= halfLength && Math.abs(across) <= halfWidth;
		}
	}

	/** One floor's channels after rotation, mirroring and party scaling. */
	public record Plan(int radius, int ringRadius, double halfWidth, int edgeMargin, List<double[]> radials, List<Bridge> bridges) {
		/** Distance from a column to the nearest channel's edge (negative inside a channel). */
		public double distance(double x, double z) {
			double d = Math.sqrt(x * x + z * z);
			double best = Math.abs(d - ringRadius) - halfWidth;
			double end = radius - edgeMargin;
			for (double[] dir : radials) {
				double along = x * dir[0] + z * dir[1];
				double across = Math.abs(-x * dir[1] + z * dir[0]);
				double t = Math.max(ringRadius, Math.min(end, along));
				double ex = x - dir[0] * t;
				double ez = z - dir[1] * t;
				double dist = along < ringRadius - halfWidth ? Double.MAX_VALUE : t == along ? across : Math.sqrt(ex * ex + ez * ez);
				best = Math.min(best, dist - halfWidth);
			}
			return best;
		}

		public boolean inChannel(double x, double z) {
			return distance(x, z) <= 0;
		}

		/** The bridge over a column, or null. */
		public Bridge bridgeAt(double x, double z) {
			for (Bridge bridge : bridges) {
				if (bridge.covers(x, z)) {
					return bridge;
				}
			}
			return null;
		}
	}

	private LavaChannels() {
	}

	public static Plan plan(Spec spec, int radius, int rotation, boolean mirrored) {
		int ring = (int) Math.round(radius * spec.ringFraction);
		double half = spec.width / 2.0;
		int end = radius - Math.max(1, spec.edgeMargin);
		List<double[]> radials = new ArrayList<>();
		for (double angle : spec.radials) {
			radials.add(direction(angle, rotation, mirrored));
		}
		List<Bridge> bridges = new ArrayList<>();
		double halfLength = half + 2;
		double halfWidth = Math.max(1, spec.bridgeWidth) / 2.0;
		for (int i = 0; i < spec.ringBridges.size(); i++) {
			double[] dir = direction(spec.ringBridges.get(i), rotation, mirrored);
			bridges.add(new Bridge(dir[0] * ring, dir[1] * ring, dir[0], dir[1], halfLength, halfWidth, i == spec.highBridge));
		}
		for (double[] dir : radials) {
			double t = ring + (end - ring) * spec.radialBridgeAt;
			// Crossing a radial goes sideways (perpendicular to it).
			bridges.add(new Bridge(dir[0] * t, dir[1] * t, -dir[1], dir[0], halfLength, halfWidth, spec.radialBridgesHigh));
		}
		return new Plan(radius, ring, half, Math.max(1, spec.edgeMargin), List.copyOf(radials), List.copyOf(bridges));
	}

	/** A template-space angle as a unit vector after the floor's mirroring (x → −x) and quarter turns. */
	static double[] direction(double degrees, int rotation, boolean mirrored) {
		double x = Math.cos(Math.toRadians(degrees));
		double z = Math.sin(Math.toRadians(degrees));
		if (mirrored) {
			x = -x;
		}
		return switch (rotation & 3) {
			case 1 -> new double[] {-z, x};
			case 2 -> new double[] {-x, -z};
			case 3 -> new double[] {z, -x};
			default -> new double[] {x, z};
		};
	}
}
