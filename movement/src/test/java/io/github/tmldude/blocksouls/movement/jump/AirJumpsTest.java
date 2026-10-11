package io.github.tmldude.blocksouls.movement.jump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.movement.MovementConfig;

class AirJumpsTest {
	@Test
	void oneAirJumpPerLevel() {
		AirJumps jumps = new AirJumps();
		jumps.tick(false);
		assertTrue(jumps.tryUse(2));
		assertTrue(jumps.tryUse(2));
		assertFalse(jumps.tryUse(2));
		assertEquals(2, jumps.used());
	}

	@Test
	void noneWithoutTheTrait() {
		assertFalse(new AirJumps().tryUse(0));
	}

	@Test
	void landingResets() {
		AirJumps jumps = new AirJumps();
		assertTrue(jumps.tryUse(1));
		jumps.tick(false);
		assertFalse(jumps.tryUse(1));
		jumps.tick(true);
		assertTrue(jumps.tryUse(1));
	}

	@Test
	void airPressAfterCoyoteTimeIsReportedAndCanBeCancelled() {
		MovementConfig.Jump config = new MovementConfig.Jump();
		JumpController controller = new JumpController(config);
		controller.tick(new JumpController.Input(true, false, 0, 0.08, true));
		for (int t = 0; t <= config.coyoteTicks; t++) {
			controller.tick(new JumpController.Input(false, false, -0.1, 0.08, true));
		}
		JumpController.Result press = controller.tick(new JumpController.Input(false, true, -0.1, 0.08, true));
		assertTrue(press.airPress());
		assertFalse(press.jump());

		// Spent on an air jump: landing must not fire the buffered press too.
		controller.cancelBuffer();
		assertFalse(controller.tick(new JumpController.Input(true, true, 0, 0.08, true)).jump());
	}

	@Test
	void coyoteJumpIsNotAnAirPress() {
		JumpController controller = new JumpController(new MovementConfig.Jump());
		controller.tick(new JumpController.Input(true, false, 0, 0.08, true));
		controller.tick(new JumpController.Input(false, false, -0.08, 0.08, true));
		JumpController.Result press = controller.tick(new JumpController.Input(false, true, -0.1, 0.08, true));
		assertTrue(press.jump());
		assertFalse(press.airPress());
	}

	@Test
	void thePressThatLaunchesAJumpIsNotAnAirPress() {
		JumpController controller = new JumpController(new MovementConfig.Jump());
		controller.tick(new JumpController.Input(true, false, 0, 0.08, true));
		// Vanilla jumps during the player tick; the controller runs after, already airborne, with the key just pressed.
		controller.onJump();
		JumpController.Result launch = controller.tick(new JumpController.Input(false, true, 0.36, 0.08, true));
		assertFalse(launch.airPress());
		assertFalse(launch.jump());

		controller.tick(new JumpController.Input(false, false, 0.3, 0.08, true));
		assertTrue(controller.tick(new JumpController.Input(false, true, 0.2, 0.08, true)).airPress());
	}
}
