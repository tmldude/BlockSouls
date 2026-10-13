package io.github.tmldude.blocksouls.abilities.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CooldownMathTest {
	@Test
	void reductionShortensAndClamps() {
		assertEquals(500, CooldownMath.withReduction(500, 0));
		assertEquals(400, CooldownMath.withReduction(500, 0.2));
		assertEquals(25, CooldownMath.withReduction(500, 2.0), "reduction caps at 95%");
		assertEquals(1, CooldownMath.withReduction(0, 0.5), "never below one tick");
	}

	@Test
	void remainingAndReady() {
		assertEquals(100, CooldownMath.remaining(1100, 1000));
		assertEquals(0, CooldownMath.remaining(1000, 1100));
		assertFalse(CooldownMath.ready(1100, 1000));
		assertTrue(CooldownMath.ready(1100, 1100));
	}

	@Test
	void progressSweepsFromZeroToOne() {
		assertEquals(0.0F, CooldownMath.progress(1000, 1500, 1000), 1e-6);
		assertEquals(0.5F, CooldownMath.progress(1000, 1500, 1250), 1e-6);
		assertEquals(1.0F, CooldownMath.progress(1000, 1500, 1700), 1e-6);
		assertEquals(1.0F, CooldownMath.progress(1000, 1000, 1000), 1e-6, "zero-length cooldown is ready");
	}

	@Test
	void secondsLeftRoundsUp() {
		assertEquals(5, CooldownMath.secondsLeft(1100, 1000));
		assertEquals(1, CooldownMath.secondsLeft(1001, 1000));
		assertEquals(0, CooldownMath.secondsLeft(1000, 1000));
	}
}
