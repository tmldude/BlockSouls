package io.github.tmldude.blocksouls.movement.climb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.movement.MovementConfig;

class ClimbTest {
	final MovementConfig.Climb config = new MovementConfig.Climb();

	@Test
	void climbsOnlyAirborneAgainstAWallWithStamina() {
		assertTrue(Climb.isClimbing(false, true, false, true, 50, config));
		assertFalse(Climb.isClimbing(true, true, false, true, 50, config), "on the ground");
		assertFalse(Climb.isClimbing(false, false, false, true, 50, config), "no wall");
		assertFalse(Climb.isClimbing(false, true, true, true, 50, config), "sneaking lets go");
		assertFalse(Climb.isClimbing(false, true, false, false, 50, config), "ineligible");
		assertFalse(Climb.isClimbing(false, true, false, true, 0, config), "out of stamina");
	}

	@Test
	void neverSlowsAFasterRise() {
		assertEquals(config.climbSpeed, Climb.climbVelocity(-0.5, config));
		assertEquals(0.42, Climb.climbVelocity(0.42, config));
	}

	@Test
	void pullsUpNearTheTopWhenThereIsRoom() {
		assertTrue(Climb.isLedge(11.2, 10, true, config));
		assertFalse(Climb.isLedge(11.2, 10, false, config), "no room on top");
		assertFalse(Climb.isLedge(12, 10, true, config), "top still too far");
	}

	@Test
	void drainsFiveStaminaPerSecond() {
		assertEquals(5, Climb.staminaPerTick(config) * 20, 1e-9);
	}
}
