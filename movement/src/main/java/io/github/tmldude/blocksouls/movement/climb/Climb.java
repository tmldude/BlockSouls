package io.github.tmldude.blocksouls.movement.climb;

import io.github.tmldude.blocksouls.movement.MovementConfig;

/** Pure wall-climb rules; the world queries live in the callers. */
public final class Climb {
	private Climb() {
	}

	/**
	 * Whether the player climbs this tick.
	 *
	 * @param touchingWall a solid block face is within reach in the input direction
	 * @param sneaking     sneak lets go of the wall
	 * @param eligible     false while flying, gliding, in liquid, on a ladder, riding, etc.
	 */
	public static boolean isClimbing(boolean onGround, boolean touchingWall, boolean sneaking, boolean eligible, float stamina,
			MovementConfig.Climb config) {
		return config.enabled && eligible && !onGround && touchingWall && !sneaking && stamina > 0;
	}

	/** Vertical velocity while climbing: climb speed, unless already rising faster. */
	public static double climbVelocity(double velocityY, MovementConfig.Climb config) {
		return climbVelocity(velocityY, config, 1.0);
	}

	/** @param speedMultiplier scales the climb speed (the {@code CLIMB_SPEED} trait) */
	public static double climbVelocity(double velocityY, MovementConfig.Climb config, double speedMultiplier) {
		return Math.max(velocityY, config.climbSpeed * speedMultiplier);
	}

	/**
	 * Whether to pull up over the wall now.
	 *
	 * @param wallTop    Y of the top of the wall in front
	 * @param feetY      the player's feet Y
	 * @param spaceAbove whether the player fits standing on top of the wall
	 */
	public static boolean isLedge(double wallTop, double feetY, boolean spaceAbove, MovementConfig.Climb config) {
		return spaceAbove && wallTop - feetY <= config.ledgeGrabHeight;
	}

	public static double staminaPerTick(MovementConfig.Climb config) {
		return config.staminaPerSecond / 20.0;
	}
}
