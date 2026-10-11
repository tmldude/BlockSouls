package io.github.tmldude.blocksouls.movement.web;

import io.github.tmldude.blocksouls.movement.MovementConfig;

/**
 * Web-swing physics: a rope that only pulls (never pushes), integrated once per tick. Pure, unit-tested.
 *
 * <p>Positions are the rope's attach point on the player (see {@link #ROPE_ATTACH_HEIGHT}); velocities are
 * blocks/tick, i.e. what the player will move next tick.
 */
public final class Pendulum {
	/** Where the rope meets the player: this far above the feet (about hand height). */
	public static final double ROPE_ATTACH_HEIGHT = 1.2;
	/** The rope counts as taut within this distance of its full length. */
	private static final double TAUT_EPSILON = 0.05;
	private static final double EPSILON = 1.0E-6;

	private Pendulum() {
	}

	public record Vec(double x, double y, double z) {
		public static final Vec ZERO = new Vec(0, 0, 0);

		public Vec add(Vec o) {
			return new Vec(x + o.x, y + o.y, z + o.z);
		}

		public Vec subtract(Vec o) {
			return new Vec(x - o.x, y - o.y, z - o.z);
		}

		public Vec scale(double s) {
			return new Vec(x * s, y * s, z * s);
		}

		public double dot(Vec o) {
			return x * o.x + y * o.y + z * o.z;
		}

		public double length() {
			return Math.sqrt(dot(this));
		}

		public Vec withLength(double newLength) {
			double length = length();
			return length < EPSILON ? this : scale(newLength / length);
		}
	}

	/** Velocity for next tick and the (possibly reeled-in) rope length. */
	public record Step(Vec velocity, double length) {
	}

	/** Rope length for a web shot from {@code distance} away. */
	public static double initialLength(double distance, MovementConfig.Web config) {
		return Math.max(config.minLength, distance);
	}

	/**
	 * One tick of swinging.
	 *
	 * @param pos     rope attach point on the player now
	 * @param vel     the player's velocity (last tick's movement)
	 * @param reel    sneak is held: shorten the rope
	 * @param gravity the player's gravity (vanilla 0.08)
	 */
	public static Step step(Vec pos, Vec vel, Vec anchor, double length, boolean reel, double gravity, MovementConfig.Web config) {
		if (reel) {
			length = Math.max(config.minLength, length - config.reelSpeed);
		}

		Vec v = vel.subtract(new Vec(0, gravity, 0));
		Vec offset = pos.subtract(anchor);
		double distance = offset.length();

		if (distance > EPSILON && distance >= length - TAUT_EPSILON) {
			Vec normal = offset.scale(1.0 / distance);
			double radial = v.dot(normal);
			if (radial > 0) {
				v = v.subtract(normal.scale(radial));
			}

			// Pump the swing on the way down, along the direction of travel around the arc.
			if (v.y() < 0) {
				Vec tangent = v.subtract(normal.scale(v.dot(normal)));
				if (tangent.length() > EPSILON) {
					v = v.add(tangent.withLength(config.swingBoost));
				}
			}
		}

		v = v.scale(config.drag);
		v = constrain(pos, v, anchor, length, distance <= length + TAUT_EPSILON);

		if (v.length() > config.maxSpeed) {
			v = v.withLength(config.maxSpeed);
		}
		return new Step(v, length);
	}

	/** Velocity that keeps next tick's position on or inside the rope's sphere. */
	private static Vec constrain(Vec pos, Vec v, Vec anchor, double length, boolean keepSpeed) {
		Vec projected = projectInside(pos, v, anchor, length);
		if (projected == v || !keepSpeed) {
			// Overstretched (reeling in): the pull back is extra speed, so don't rescale it away.
			return projected;
		}

		// Projecting onto the sphere shortens the step (chord vs. arc); give that speed back so swinging doesn't
		// bleed energy, then project once more to stay on the sphere.
		return projectInside(pos, projected.withLength(v.length()), anchor, length);
	}

	private static Vec projectInside(Vec pos, Vec v, Vec anchor, double length) {
		Vec next = pos.add(v).subtract(anchor);
		double distance = next.length();
		if (distance <= length) {
			return v;
		}
		return anchor.add(next.scale(length / distance)).subtract(pos);
	}

	/** Velocity when letting go: keep the swing, plus a small hop. */
	public static Vec release(Vec vel, MovementConfig.Web config) {
		return vel.add(new Vec(0, config.releaseBoost, 0));
	}

	/** Server check: whether the player has drifted too far from the anchor to still be on this rope. */
	public static boolean isTooFar(double distance, double length, MovementConfig.Web config) {
		return distance > length + config.serverSlack;
	}
}
