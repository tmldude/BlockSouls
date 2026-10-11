package io.github.tmldude.blocksouls.movement.roll;

import io.github.tmldude.blocksouls.movement.MoveMath;
import io.github.tmldude.blocksouls.movement.MovementConfig;

/** Pure dodge-roll timing and direction math. Tick 0 is the tick the roll starts. */
public final class Roll {
	private Roll() {
	}

	public static boolean isActive(int tick, MovementConfig.Roll config) {
		return tick >= 0 && tick < config.durationTicks;
	}

	public static boolean isInvulnerable(int tick, MovementConfig.Roll config) {
		return tick >= config.iframeStart && tick <= config.iframeEnd;
	}

	/** Horizontal speed on the given tick: full speed, then a linear ease-out to 0 at the end. */
	public static double speedAt(int tick, MovementConfig.Roll config) {
		if (!isActive(tick, config)) {
			return 0;
		}
		if (tick < config.fullSpeedTicks) {
			return config.speed;
		}
		int easeTicks = config.durationTicks - config.fullSpeedTicks;
		return config.speed * (config.durationTicks - tick) / (easeTicks + 1);
	}

	/** World-space unit direction {x, z} for a roll: the movement input, or the facing direction if none. */
	public static double[] direction(double strafe, double forward, float yawDegrees) {
		double[] direction = MoveMath.inputDirection(strafe, forward, yawDegrees);
		return direction != null ? direction : MoveMath.inputDirection(0, 1, yawDegrees);
	}
}
