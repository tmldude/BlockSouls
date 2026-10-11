package io.github.tmldude.blocksouls.movement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MoveMathTest {
	@Test
	void noInputHasNoDirection() {
		assertNull(MoveMath.inputDirection(0, 0, 45));
		assertNull(MoveMath.inputDirection(true, true, false, false, 45));
	}

	@Test
	void keyStatesMatchAnalogInput() {
		assertArrayEquals(MoveMath.inputDirection(1, 1, 30), MoveMath.inputDirection(true, false, true, false, 30), 1e-9);
		assertArrayEquals(MoveMath.inputDirection(-1, -1, 30), MoveMath.inputDirection(false, true, false, true, 30), 1e-9);
	}

	@Test
	void forwardAtYawZeroIsSouth() {
		assertArrayEquals(new double[] {0, 1}, MoveMath.inputDirection(true, false, false, false, 0), 1e-9);
	}
}
