package io.github.tmldude.blocksouls.movement.jump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.movement.MovementConfig;

class JumpControllerTest {
	static final double GRAVITY = 0.08;
	static final double VANILLA_JUMP = 0.42;

	final MovementConfig.Jump config = new MovementConfig.Jump();
	final JumpController controller = new JumpController(config);

	/** Minimal vanilla air physics: move, then apply gravity and drag; the controller runs after movement. */
	class Sim {
		double y;
		double vy;
		boolean onGround = true;
		double peak;

		void launch() {
			vy = VANILLA_JUMP * controller.onJump();
			onGround = false;
		}

		/** Advances one tick; returns whether the controller asked for a jump. */
		boolean tick(boolean jumpDown) {
			if (!onGround) {
				y += vy;
				vy = (vy - GRAVITY) * 0.98;
				if (y <= 0) {
					y = 0;
					vy = 0;
					onGround = true;
				}
			}
			peak = Math.max(peak, y);

			JumpController.Result result = controller.tick(new JumpController.Input(onGround, jumpDown, vy, GRAVITY, true));
			vy = result.velocityY();
			if (result.jump()) {
				launch();
			}
			return result.jump();
		}

		/** Runs until landing, holding jump for the first {@code holdTicks} ticks. */
		void untilLanded(int holdTicks) {
			for (int t = 0; t < 200 && (t == 0 || !onGround); t++) {
				tick(t < holdTicks);
			}
		}
	}

	static double vanillaJumpHeight() {
		double y = 0;
		double vy = VANILLA_JUMP;
		double peak = 0;
		while (y >= 0) {
			y += vy;
			vy = (vy - GRAVITY) * 0.98;
			peak = Math.max(peak, y);
		}
		return peak;
	}

	@Test
	void tappedJumpIsLowerAndHeldJumpIsHigherThanVanilla() {
		double vanilla = vanillaJumpHeight();

		Sim tap = new Sim();
		tap.launch();
		tap.untilLanded(1);

		// Separate controller so the triple-jump chain from the first sim doesn't apply.
		Sim held = new JumpControllerTest().new Sim();
		held.launch();
		held.untilLanded(100);

		assertTrue(tap.peak < vanilla, "tap " + tap.peak + " vs vanilla " + vanilla);
		assertTrue(held.peak > vanilla, "held " + held.peak + " vs vanilla " + vanilla);
		assertTrue(held.peak < 2.0, "held jump must stay under 2 blocks, was " + held.peak);
	}

	@Test
	void fallsFasterThanVanillaButCapsSpeed() {
		Sim sim = new Sim();
		sim.y = 100;
		sim.onGround = false;
		for (int i = 0; i < 10; i++) {
			sim.tick(false);
		}

		double vanillaY = 100;
		double vanillaVy = 0;
		for (int i = 0; i < 10; i++) {
			vanillaY += vanillaVy;
			vanillaVy = (vanillaVy - GRAVITY) * 0.98;
		}
		assertTrue(sim.y < vanillaY);

		for (int i = 0; i < 200; i++) {
			JumpController.Result result = controller.tick(new JumpController.Input(false, false, -3.95, GRAVITY, true));
			assertEquals(-3.95, result.velocityY(), 1e-9, "no extra gravity past the cap");
		}
	}

	@Test
	void coyoteTimeAllowsAJumpJustAfterWalkingOffALedge() {
		controller.tick(new JumpController.Input(true, false, 0, GRAVITY, true));
		for (int t = 1; t < config.coyoteTicks; t++) {
			controller.tick(new JumpController.Input(false, false, -0.08, GRAVITY, true));
		}
		assertTrue(controller.tick(new JumpController.Input(false, true, -0.1, GRAVITY, true)).jump());
	}

	@Test
	void noCoyoteJumpAfterTheWindow() {
		controller.tick(new JumpController.Input(true, false, 0, GRAVITY, true));
		for (int t = 0; t < config.coyoteTicks; t++) {
			controller.tick(new JumpController.Input(false, false, -0.08, GRAVITY, true));
		}
		assertFalse(controller.tick(new JumpController.Input(false, true, -0.2, GRAVITY, true)).jump());
	}

	@Test
	void noCoyoteJumpAfterAlreadyJumping() {
		Sim sim = new Sim();
		sim.tick(false);
		sim.launch();
		sim.tick(false);
		assertFalse(sim.tick(true), "pressing again right after a jump must not double-jump");
	}

	@Test
	void bufferedPressFiresOnLanding() {
		// Find the landing tick, then replay and press exactly jumpBufferTicks before it.
		int landing = 0;
		Sim probe = new Sim();
		probe.y = 3;
		probe.onGround = false;
		while (!probe.onGround) {
			probe.tick(false);
			landing++;
		}

		JumpController buffered = new JumpController(config);
		assertTrue(landsWithJump(buffered, landing, landing - config.jumpBufferTicks));
		assertFalse(landsWithJump(new JumpController(config), landing, landing - config.jumpBufferTicks - 1));
	}

	private boolean landsWithJump(JumpController c, int landingTick, int pressTick) {
		// Start mid-jump so the press can't be a coyote jump.
		c.tick(new JumpController.Input(true, false, 0, GRAVITY, true));
		c.onJump();
		for (int t = 1; t < landingTick; t++) {
			assertFalse(c.tick(new JumpController.Input(false, t == pressTick, -0.5, GRAVITY, true)).jump());
		}
		return c.tick(new JumpController.Input(true, false, 0, GRAVITY, true)).jump();
	}

	@Test
	void tripleJumpChainsQuickJumpsThenResets() {
		Sim sim = new Sim();
		sim.tick(false);
		double[] mults = new double[4];
		for (int i = 0; i < 4; i++) {
			mults[i] = controller.onJump();
			sim.onGround = false;
			sim.vy = VANILLA_JUMP * mults[i];
			sim.untilLanded(0);
			sim.tick(false);
		}

		assertEquals(config.tapJumpMult, mults[0], 1e-9);
		assertEquals(config.tapJumpMult * (1 + config.secondJumpBonus), mults[1], 1e-9);
		assertEquals(config.tapJumpMult * (1 + config.thirdJumpBonus), mults[2], 1e-9);
		assertEquals(config.tapJumpMult, mults[3], 1e-9, "chain restarts after the third jump");
	}

	@Test
	void firstJumpFromStandingIsNotAChainJump() {
		controller.tick(new JumpController.Input(true, false, 0, GRAVITY, true));
		assertEquals(config.tapJumpMult, controller.onJump(), 1e-9);
	}

	@Test
	void tripleJumpChainBreaksAfterStandingStill() {
		controller.tick(new JumpController.Input(true, false, 0, GRAVITY, true));
		controller.onJump();
		controller.tick(new JumpController.Input(false, false, 0.3, GRAVITY, true));
		for (int t = 0; t <= config.tripleJumpWindowTicks + 1; t++) {
			controller.tick(new JumpController.Input(true, false, 0, GRAVITY, true));
		}
		assertEquals(0, chainStepAfterJump());
	}

	private int chainStepAfterJump() {
		controller.onJump();
		return controller.chainStep();
	}

	@Test
	void ineligibleTicksPassVelocityThrough() {
		controller.onJump();
		JumpController.Result result = controller.tick(new JumpController.Input(false, true, -1.0, GRAVITY, false));
		assertEquals(-1.0, result.velocityY());
		assertFalse(result.jump());
	}
}
