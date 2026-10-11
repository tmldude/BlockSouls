package io.github.tmldude.blocksouls.movement;

import org.jspecify.annotations.Nullable;

/** Movement-input math shared by the movement features. Pure, unit-tested. */
public final class MoveMath {
	private MoveMath() {
	}

	/**
	 * World-space unit direction {x, z} of a movement input (strafe = left positive, forward = forward
	 * positive), rotated by yaw the way vanilla does. Null when there's no input.
	 */
	public static double @Nullable [] inputDirection(double strafe, double forward, float yawDegrees) {
		double length = Math.sqrt(strafe * strafe + forward * forward);
		if (length < 1.0E-4) {
			return null;
		}
		strafe /= length;
		forward /= length;

		double yaw = Math.toRadians(yawDegrees);
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		return new double[] {strafe * cos - forward * sin, forward * cos + strafe * sin};
	}

	/** Same convention for key states, as the server sees them in the player's last input. */
	public static double @Nullable [] inputDirection(boolean forward, boolean backward, boolean left, boolean right, float yawDegrees) {
		return inputDirection(axis(left, right), axis(forward, backward), yawDegrees);
	}

	private static double axis(boolean positive, boolean negative) {
		return positive == negative ? 0 : positive ? 1 : -1;
	}
}
