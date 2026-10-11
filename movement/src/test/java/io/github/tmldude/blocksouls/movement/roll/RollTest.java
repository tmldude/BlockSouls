package io.github.tmldude.blocksouls.movement.roll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.movement.MovementConfig;

class RollTest {
	final MovementConfig.Roll config = new MovementConfig.Roll();

	@Test
	void iframesCoverTicksTwoThroughEight() {
		for (int t = 0; t < config.durationTicks; t++) {
			assertEquals(t >= 2 && t <= 8, Roll.isInvulnerable(t, config), "tick " + t);
		}
	}

	@Test
	void lastsTwelveTicks() {
		assertTrue(Roll.isActive(11, config));
		assertFalse(Roll.isActive(12, config));
		assertFalse(Roll.isActive(-1, config));
	}

	@Test
	void speedHoldsThenEasesToZero() {
		assertEquals(config.speed, Roll.speedAt(0, config));
		assertEquals(config.speed, Roll.speedAt(config.fullSpeedTicks - 1, config));
		double previous = config.speed;
		for (int t = config.fullSpeedTicks; t < config.durationTicks; t++) {
			double speed = Roll.speedAt(t, config);
			assertTrue(speed < previous && speed > 0, "tick " + t + " speed " + speed);
			previous = speed;
		}
		assertEquals(0, Roll.speedAt(config.durationTicks, config));
	}

	@Test
	void directionFollowsVanillaYawConvention() {
		// yaw 0 faces +Z (south), yaw 90 faces -X (west).
		assertVec(0, 1, Roll.direction(0, 1, 0));
		assertVec(-1, 0, Roll.direction(0, 1, 90));
		// Strafing left (positive strafe) at yaw 0 goes +X.
		assertVec(1, 0, Roll.direction(1, 0, 0));
		// Backwards.
		assertVec(0, -1, Roll.direction(0, -1, 0));
	}

	@Test
	void noInputRollsTheWayThePlayerFaces() {
		assertVec(-1, 0, Roll.direction(0, 0, 90));
	}

	@Test
	void diagonalInputIsNormalized() {
		double[] d = Roll.direction(1, 1, 0);
		assertEquals(1, Math.hypot(d[0], d[1]), 1e-9);
	}

	private static void assertVec(double x, double z, double[] actual) {
		assertEquals(x, actual[0], 1e-9, "x");
		assertEquals(z, actual[1], 1e-9, "z");
	}
}
